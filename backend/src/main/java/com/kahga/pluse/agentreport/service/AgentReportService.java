package com.kahga.pluse.agentreport.service;

import com.kahga.pluse.accessrequest.entity.AccessRequest;
import com.kahga.pluse.accessrequest.service.AccessRequestService;
import com.kahga.pluse.agentreport.dto.AgentReportDto;
import com.kahga.pluse.agentreport.dto.AgentReportScopeDto;
import com.kahga.pluse.agentreport.repository.AgentReportRepository;
import com.kahga.pluse.common.exception.BusinessException;
import com.kahga.pluse.location.entity.Unit;
import com.kahga.pluse.location.entity.UnitLevel;
import com.kahga.pluse.location.service.LocationService;
import com.kahga.pluse.sentiment.entity.ConfidenceLevel;
import com.kahga.pluse.sentiment.entity.SentimentValue;
import com.kahga.pluse.voterexport.dto.ExportVoterRow;
import com.kahga.pluse.voterexport.dto.SentimentFilter;
import com.kahga.pluse.voterexport.service.VoterExportService;
import com.kahga.pluse.voterexport.service.VoterListPdfBuilder;
import com.kahga.pluse.user.entity.User;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-U14: the field agent's own Reports page.
 *
 * <p>Three gates, all here and all checked on every call (RA-N3):
 *
 * <ol>
 *   <li>the agent must have been given the Reports page (AR-1);
 *   <li>the panchayat and candidate must come from one of their own live
 *       access requests (AR-3, AR-4);
 *   <li>the figures cover the booths under that request and nothing else
 *       (AR-5).
 * </ol>
 *
 * <p>The page sends a panchayat id, but it is never trusted as a scope: it is
 * matched against the agent's grants, and the booths come from the grant that
 * matched. An agent who edits the request to name another panchayat gets a 403,
 * not somebody else's booths.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentReportService {

    private final AccessRequestService accessRequestService;
    private final LocationService locationService;
    private final AgentReportRepository agentReportRepository;
    private final VoterListPdfBuilder pdfBuilder;

    /** AR-1. The tab is hidden without it; this is what makes that real. */
    public void requireReportsAccess(User agent) {
        if (!agent.isReportsEnabled()) {
            throw new BusinessException(
                    "The Reports page is not switched on for your account", HttpStatus.FORBIDDEN);
        }
    }

    /**
     * Every panchayat and candidate pairing the agent may report on, newest
     * approval first. Empty when they hold no live approval, which the page
     * shows as "No approved booths" (AR-2).
     */
    public List<AgentReportScopeDto> scope(User agent) {
        requireReportsAccess(agent);

        List<AgentReportScopeDto> scopes = new ArrayList<>();
        for (AccessRequest grant : sortedGrants(agent)) {
            Unit panchayat = panchayatOf(grant);
            List<UUID> boothIds = boothIdsOf(grant);
            List<AgentReportScopeDto.BoothRef> booths = boothIds.stream()
                    .map(locationService::require)
                    .sorted(Comparator.comparing(Unit::getName))
                    .map(booth -> new AgentReportScopeDto.BoothRef(booth.getId(), booth.getName()))
                    .toList();
            String[] path = panchayat.getPath() == null ? new String[0] : panchayat.getPath().split(" > ");

            scopes.add(new AgentReportScopeDto(
                    panchayat.getId(),
                    panchayat.getName(),
                    path.length > 1 ? path[1] : null,
                    path.length > 0 ? path[0] : null,
                    grant.getCandidate().getId(),
                    grant.getCandidate().getName(),
                    grant.getExpiresAt(),
                    booths,
                    agentReportRepository.wardsIn(boothIds)));
        }
        return scopes;
    }

    /**
     * The report itself (AG-5).
     *
     * @param days null or 0 for the whole campaign, otherwise how far back to
     *     count entries
     */
    public AgentReportDto report(
            User agent,
            UUID panchayatId,
            UUID candidateId,
            Collection<UUID> chosenBooths,
            Collection<ConfidenceLevel> confidence,
            Collection<Integer> wards,
            Collection<String> castes,
            Integer days) {

        requireReportsAccess(agent);
        AccessRequest grant = requireGrant(agent, panchayatId, candidateId);
        List<UUID> boothIds = narrowToChosen(boothIdsOf(grant), chosenBooths);
        AgentReportRepository.CasteChoice casteChoice = AgentReportRepository.CasteChoice.of(castes);

        Instant since = days == null || days <= 0 ? null : Instant.now().minus(days, ChronoUnit.DAYS);
        Map<UUID, Map<SentimentValue, Long>> sentiment = agentReportRepository.sentimentByBooth(
                boothIds, candidateId, confidence, wards, casteChoice, since);
        Map<UUID, AgentReportRepository.Roll> rolls =
                agentReportRepository.rollByBooth(boothIds, candidateId, wards, casteChoice);

        List<AgentReportDto.BoothRow> rows = new ArrayList<>();
        long positive = 0;
        long neutral = 0;
        long negative = 0;
        long notRecorded = 0;
        long voters = 0;

        for (UUID boothId : boothIds) {
            Unit booth = locationService.require(boothId);
            Map<SentimentValue, Long> counts = sentiment.getOrDefault(boothId, Map.of());
            AgentReportRepository.Roll roll = rolls.getOrDefault(boothId, AgentReportRepository.Roll.EMPTY);

            long boothPositive = counts.getOrDefault(SentimentValue.POSITIVE, 0L);
            long boothNeutral = counts.getOrDefault(SentimentValue.NEUTRAL, 0L);
            long boothNegative = counts.getOrDefault(SentimentValue.NEGATIVE, 0L);
            long boothNotRecorded = Math.max(0, roll.voters() - roll.recorded());

            rows.add(new AgentReportDto.BoothRow(
                    boothId,
                    booth.getName(),
                    boothPositive,
                    boothNeutral,
                    boothNegative,
                    boothNotRecorded,
                    roll.voters()));

            positive += boothPositive;
            neutral += boothNeutral;
            negative += boothNegative;
            notRecorded += boothNotRecorded;
            voters += roll.voters();
        }
        rows.sort(Comparator.comparing(AgentReportDto.BoothRow::boothName));

        AgentReportDto.BoothRow totals = new AgentReportDto.BoothRow(
                grant.getUnit().getId(),
                // "Your booths together", never the whole panchayat (AR-5).
                grant.getUnit().getName(),
                positive,
                neutral,
                negative,
                notRecorded,
                voters);
        return new AgentReportDto(rows, totals, rows.size());
    }

    /**
     * The booths the agent ticked, kept inside the ones their grant covers.
     *
     * <p>A booth they do not hold is refused rather than quietly dropped: a
     * request naming one is either stale or tampered with, and both deserve an
     * answer rather than a report that silently means something else (AR-5).
     */
    private List<UUID> narrowToChosen(List<UUID> approved, Collection<UUID> chosen) {
        if (chosen == null || chosen.isEmpty()) {
            return approved;
        }
        List<UUID> outside = chosen.stream().filter(id -> !approved.contains(id)).toList();
        if (!outside.isEmpty()) {
            throw new BusinessException("You have no approved access to that booth", HttpStatus.FORBIDDEN);
        }
        return approved.stream().filter(chosen::contains).toList();
    }

    /**
     * AG-9 / AR-6: the voters behind a drawn report, as the walk-list PDF.
     *
     * <p>Two switches, not one. The Reports page lets the agent see the
     * figures; the PDF-download switch is what lets them carry a list of named
     * voters out of the building, and both are checked here.
     */
    public VoterExportService.Pdf exportVoters(
            User agent,
            UUID panchayatId,
            UUID candidateId,
            Collection<UUID> chosenBooths,
            Collection<SentimentFilter> sentiments,
            Collection<ConfidenceLevel> confidence,
            Collection<Integer> wards,
            Collection<String> castes,
            Integer days) {

        requireReportsAccess(agent);
        if (!agent.isDownloadEnabled()) {
            throw new BusinessException(
                    "Voter-details download is turned off for you", HttpStatus.FORBIDDEN);
        }
        AccessRequest grant = requireGrant(agent, panchayatId, candidateId);
        List<UUID> boothIds = narrowToChosen(boothIdsOf(grant), chosenBooths);
        Set<SentimentFilter> chosen = sentiments == null || sentiments.isEmpty()
                ? Set.of(SentimentFilter.values())
                : new LinkedHashSet<>(sentiments);

        Map<UUID, Unit> booths = new LinkedHashMap<>();
        boothIds.forEach(id -> booths.put(id, locationService.require(id)));

        Instant since = days == null || days <= 0 ? null : Instant.now().minus(days, ChronoUnit.DAYS);
        List<ExportVoterRow> rows = agentReportRepository.voterRows(
                boothIds,
                booths,
                grant.getCandidate().getId(),
                chosen,
                confidence,
                wards,
                AgentReportRepository.CasteChoice.of(castes),
                since,
                VoterExportService.MAX_ROWS + 1);

        if (rows.isEmpty()) {
            throw new BusinessException("No voters match that selection");
        }
        if (rows.size() > VoterExportService.MAX_ROWS) {
            throw new BusinessException(
                    "That is more than the %,d voters a single file holds \u2014 narrow the choices"
                            .formatted(VoterExportService.MAX_ROWS),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        Unit panchayat = panchayatOf(grant);
        byte[] bytes = pdfBuilder.build(new VoterListPdfBuilder.Request(
                panchayat.getName(),
                panchayat.getPath(),
                grant.getCandidate().getName(),
                chosen,
                rows,
                agent.getName(),
                Instant.now(),
                // One file can span several booths, so each row names its own.
                true));

        String name = "%s_%s_voter-details_%s.pdf"
                .formatted(
                        safe(panchayat.getName()),
                        safe(grant.getCandidate().getName()),
                        LocalDate.now());
        return new VoterExportService.Pdf(name, bytes);
    }

    private static String safe(String value) {
        return value == null ? "Report" : value.replaceAll("[^A-Za-z0-9]+", "");
    }

    /**
     * The grant the request names, or 403. Matching on both the panchayat and
     * the candidate: an agent with two grants in one panchayat for different
     * candidates must not see one candidate's figures under the other's name.
     */
    private AccessRequest requireGrant(User agent, UUID panchayatId, UUID candidateId) {
        return sortedGrants(agent).stream()
                .filter(grant -> panchayatOf(grant).getId().equals(panchayatId))
                .filter(grant -> candidateId == null
                        || grant.getCandidate().getId().equals(candidateId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "You have no approved access to that panchayat", HttpStatus.FORBIDDEN));
    }

    /** Live approvals only: an expired one drops its booths at once (AR-2). */
    private List<AccessRequest> sortedGrants(User agent) {
        List<AccessRequest> grants = new ArrayList<>(accessRequestService.liveGrants(agent.getId()));
        grants.sort(Comparator.comparing(
                grant -> grant.getDecidedAt() == null ? Instant.EPOCH : grant.getDecidedAt(),
                Comparator.reverseOrder()));
        return grants;
    }

    /**
     * The panchayat a grant belongs to. Grants are asked for a panchayat at a
     * time now, but older ones sit on a booth or a block; the panchayat above
     * or below is what the page reports on either way.
     */
    private Unit panchayatOf(AccessRequest grant) {
        Unit unit = grant.getUnit();
        while (unit != null && unit.getLevel() != UnitLevel.PANCHAYAT) {
            if (unit.getLevel() == UnitLevel.BOOTH || unit.getLevel() == UnitLevel.PANCHAYAT) {
                break;
            }
            unit = unit.getParent() == null ? null : locationService.require(unit.getParent().getId());
        }
        if (unit == null) {
            return grant.getUnit();
        }
        if (unit.getLevel() == UnitLevel.BOOTH && unit.getParent() != null) {
            return locationService.require(unit.getParent().getId());
        }
        return unit;
    }

    private List<UUID> boothIdsOf(AccessRequest grant) {
        Set<UUID> booths = new LinkedHashSet<>(locationService.boothIdsUnder(grant.getUnit().getId()));
        return new ArrayList<>(booths);
    }
}
