package com.kahga.pluse.voterexport.service;

import com.kahga.pluse.accessrequest.entity.AccessRequest;
import com.kahga.pluse.accessrequest.service.AccessRequestService;
import com.kahga.pluse.common.exception.BusinessException;
import com.kahga.pluse.location.entity.Unit;
import com.kahga.pluse.location.service.LocationService;
import com.kahga.pluse.user.entity.User;
import com.kahga.pluse.voterexport.dto.ExportVoterRow;
import com.kahga.pluse.voterexport.dto.SentimentFilter;
import com.kahga.pluse.voterexport.dto.VoterExportSummaryDto;
import com.kahga.pluse.voterexport.repository.VoterExportRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-U13: a booth's voter list as a PDF the agent walks with.
 *
 * <p>Every call re-checks the three things that make the download legitimate —
 * a live grant for that booth, the admin's switch, and a size the agent can
 * reasonably be handed — because the phone's screen is only a convenience and
 * this endpoint is reachable without it (section 7 of the requirement).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VoterExportService {

    /**
     * DL-10: the most voters one file may hold. A list longer than this is a
     * day's walking the agent will never do, and the chips exist precisely so a
     * big booth can be taken a sentiment at a time.
     */
    public static final int MAX_ROWS = 800;

    private static final DateTimeFormatter EXPIRY =
            DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.of("Asia/Kolkata"));

    private final AccessRequestService accessRequestService;
    private final LocationService locationService;
    private final VoterExportRepository exportRepository;
    private final VoterListPdfBuilder pdfBuilder;

    /** The file and the name to save it under. */
    public record Pdf(String fileName, byte[] bytes) {}

    /**
     * What the Download page shows. The chosen chips only affect the match count
     * and the preview: each chip's own total is reported whatever is selected, so
     * the agent can see what switching one on would add.
     */
    public VoterExportSummaryDto summary(User agent, UUID boothId, UUID candidateId, Set<SentimentFilter> chosen) {
        Context context = resolve(agent, boothId, candidateId);
        long matching = exportRepository.countMatching(context.boothId(), context.candidateId(), chosen);

        List<ExportVoterRow> preview =
                exportRepository.findMatching(context.boothId(), context.candidateId(), chosen, 3);

        return new VoterExportSummaryDto(
                context.boothId(),
                context.booth().getName(),
                blockOf(context.booth()),
                context.booth().getPath(),
                context.candidateId(),
                context.grant().getCandidate().getName(),
                exportRepository.countAll(context.boothId(), context.candidateId()),
                exportRepository.counts(context.boothId(), context.candidateId()),
                matching,
                pages(matching),
                preview.stream()
                        .map(row -> new VoterExportSummaryDto.PreviewRow(
                                row.name(), row.houseNo(), row.sentimentOrNotRecorded()))
                        .toList(),
                fileName(context.booth(), chosen),
                MAX_ROWS);
    }

    /** DL-N2: built in memory and handed straight to the response; nothing is written to disk. */
    public Pdf export(User agent, UUID boothId, UUID candidateId, Set<SentimentFilter> chosen) {
        Context context = resolve(agent, boothId, candidateId);
        refuseIfTooMany(exportRepository.countMatching(context.boothId(), context.candidateId(), chosen));

        // One past the cap, so a booth that grew between the count and this read
        // is still caught rather than silently truncated.
        List<ExportVoterRow> rows =
                exportRepository.findMatching(context.boothId(), context.candidateId(), chosen, MAX_ROWS + 1);
        refuseIfTooMany(rows.size());
        if (rows.isEmpty()) {
            throw new BusinessException("No voters match that selection");
        }

        byte[] bytes = pdfBuilder.build(new VoterListPdfBuilder.Request(
                context.booth().getName(),
                context.booth().getPath(),
                context.grant().getCandidate().getName(),
                chosen,
                rows,
                agent.getName(),
                Instant.now()));

        return new Pdf(fileName(context.booth(), chosen), bytes);
    }

    /** How many pages that many rows fill, as the file will actually be laid out. */
    public static int pages(long matching) {
        return matching == 0 ? 0 : (int) Math.ceil(matching / (double) VoterListPdfBuilder.ROWS_PER_PAGE);
    }

    /**
     * {@code Booth12_Neutral-Negative_2026-11-02.pdf}. The booth number is the
     * digits in its name — "Booth 12" and the master's "12-Damodarpur" both give
     * 12 — falling back to the name itself when it holds no digits.
     */
    public static String fileName(Unit booth, Set<SentimentFilter> chosen) {
        String digits = booth.getName() == null ? "" : booth.getName().replaceAll("\\D+", "");
        String label = digits.isEmpty()
                ? (booth.getName() == null ? "Booth" : booth.getName().replaceAll("[^A-Za-z0-9]+", ""))
                : "Booth" + digits;
        return "%s_%s_%s.pdf".formatted(label, SentimentFilter.fileToken(chosen), LocalDate.now());
    }

    private void refuseIfTooMany(long matching) {
        if (matching > MAX_ROWS) {
            throw new BusinessException(
                    "That is %,d voters, more than the %,d a single file holds — narrow the sentiment selection"
                            .formatted(matching, MAX_ROWS),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    /**
     * The access checks, in the order the requirement lists them: the grant
     * first, so an agent who has lost a booth is told that rather than being
     * told downloads are off.
     */
    private Context resolve(User agent, UUID boothId, UUID candidateId) {
        if (boothId == null) {
            throw new BusinessException("Pick the booth to download");
        }
        AccessRequest grant = liveGrant(agent, boothId);

        if (candidateId != null && !grant.getCandidate().getId().equals(candidateId)) {
            throw new BusinessException("You do not have access to that booth", HttpStatus.FORBIDDEN);
        }
        if (!agent.isDownloadEnabled()) {
            throw new BusinessException(
                    "Downloads are turned off for your account. Ask your admin.", HttpStatus.FORBIDDEN);
        }

        Unit booth = locationService.require(boothId);
        return new Context(booth, grant);
    }

    /**
     * The grant, or the clearest refusal available: an agent whose approval has
     * run out is told when, since that is something they can act on.
     */
    private AccessRequest liveGrant(User agent, UUID boothId) {
        try {
            return accessRequestService.requireGrantFor(agent.getId(), boothId);
        } catch (BusinessException refused) {
            accessRequestService
                    .lastApprovedGrantFor(agent.getId(), boothId)
                    .filter(grant -> grant.getExpiresAt() != null)
                    .ifPresent(grant -> {
                        throw new BusinessException(
                                "Your access to this booth expired on " + EXPIRY.format(grant.getExpiresAt()),
                                HttpStatus.FORBIDDEN);
                    });
            throw refused;
        }
    }

    /** The block in "Booth 12, Tihidi", read off the booth's own path. */
    private String blockOf(Unit booth) {
        String[] parts = booth.getPath() == null ? new String[0] : booth.getPath().split(" > ");
        return parts.length > 1 ? parts[1] : null;
    }

    private record Context(Unit booth, AccessRequest grant) {

        UUID boothId() {
            return booth.getId();
        }

        UUID candidateId() {
            return grant.getCandidate().getId();
        }
    }
}
