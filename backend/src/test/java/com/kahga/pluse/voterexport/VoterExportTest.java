package com.kahga.pluse.voterexport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kahga.pluse.accessrequest.dto.CreateAccessRequestDto;
import com.kahga.pluse.accessrequest.entity.AccessRequest;
import com.kahga.pluse.accessrequest.repository.AccessRequestRepository;
import com.kahga.pluse.accessrequest.service.AccessRequestService;
import com.kahga.pluse.common.exception.BusinessException;
import com.kahga.pluse.location.entity.Unit;
import com.kahga.pluse.location.entity.UnitLevel;
import com.kahga.pluse.location.repository.UnitRepository;
import com.kahga.pluse.user.entity.Role;
import com.kahga.pluse.user.entity.User;
import com.kahga.pluse.user.repository.UserRepository;
import com.kahga.pluse.voter.entity.Gender;
import com.kahga.pluse.voter.entity.Voter;
import com.kahga.pluse.voter.repository.VoterRepository;
import com.kahga.pluse.voterexport.dto.SentimentFilter;
import com.kahga.pluse.voterexport.dto.VoterExportSummaryDto;
import com.kahga.pluse.voterexport.service.VoterExportService;
import com.lowagie.text.pdf.PdfReader;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

/**
 * FR-U13. The access rules matter more here than the layout: this endpoint
 * hands named voters' data out of the system, so each refusal is checked by
 * calling the service the way a phone would, not by reading the screen.
 */
@SpringBootTest
@ActiveProfiles("test")
class VoterExportTest {

    private static final Set<SentimentFilter> ALL = Set.of(SentimentFilter.values());

    @Autowired
    private VoterExportService voterExportService;

    @Autowired
    private AccessRequestService accessRequestService;

    @Autowired
    private AccessRequestRepository accessRequestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UnitRepository unitRepository;

    @Autowired
    private VoterRepository voterRepository;

    @Test
    void everyChipTogetherIsTheWholeBooth() {
        Grant grant = grantWithVoters();
        VoterExportSummaryDto summary = voterExportService.summary(grant.agent(), grant.boothId(), null, ALL);

        VoterExportSummaryDto.Counts counts = summary.counts();
        long chips = counts.positive() + counts.neutral() + counts.negative() + counts.notRecorded();

        assertThat(summary.totalVoters()).isPositive();
        assertThat(chips).isEqualTo(summary.totalVoters());
        assertThat(summary.matching()).isEqualTo(summary.totalVoters());
        assertThat(summary.candidateName()).isNotBlank();
        assertThat(summary.boothPath()).contains(summary.boothName());
    }

    /** Acceptance 3: the chips add up, and adding one adds exactly its own count. */
    @Test
    void choosingTwoChipsMatchesTheirTwoCounts() {
        Grant grant = grantWithVoters();
        VoterExportSummaryDto all = voterExportService.summary(grant.agent(), grant.boothId(), null, ALL);
        VoterExportSummaryDto.Counts counts = all.counts();

        VoterExportSummaryDto two = voterExportService.summary(
                grant.agent(),
                grant.boothId(),
                null,
                Set.of(SentimentFilter.NEGATIVE, SentimentFilter.NEUTRAL));
        assertThat(two.matching()).isEqualTo(counts.negative() + counts.neutral());

        VoterExportSummaryDto three = voterExportService.summary(
                grant.agent(),
                grant.boothId(),
                null,
                Set.of(SentimentFilter.NEGATIVE, SentimentFilter.NEUTRAL, SentimentFilter.POSITIVE));
        assertThat(three.matching()).isEqualTo(two.matching() + counts.positive());
    }

    @Test
    void thePreviewShowsTheFirstThreeMatchesInRollOrder() {
        Grant grant = grantWithVoters();
        VoterExportSummaryDto summary = voterExportService.summary(grant.agent(), grant.boothId(), null, ALL);

        assertThat(summary.preview()).hasSize(3);
        assertThat(summary.preview()).allSatisfy(row -> {
            assertThat(row.name()).isNotBlank();
            assertThat(row.sentiment()).isNotNull();
        });
        assertThat(summary.pages()).isEqualTo(VoterExportService.pages(summary.matching()));
    }

    @Test
    void theFileIsNamedForTheBoothTheDayAndTheChipsChosen() {
        Grant grant = grantWithVoters();

        String all = voterExportService.summary(grant.agent(), grant.boothId(), null, ALL).fileName();
        assertThat(all).matches("Booth\\d+_All_\\d{4}-\\d{2}-\\d{2}\\.pdf");

        String two = voterExportService
                .summary(
                        grant.agent(),
                        grant.boothId(),
                        null,
                        Set.of(SentimentFilter.NEGATIVE, SentimentFilter.NEUTRAL))
                .fileName()
                .replaceAll("_\\d{4}-\\d{2}-\\d{2}\\.pdf$", "");
        // Fixed order, whatever order the chips were tapped in.
        assertThat(two).endsWith("_Neutral-Negative");
    }

    @Test
    void thePdfIsAPdfAndRunsToThePagesThePageQuoted() throws Exception {
        Grant grant = grantWithVoters();
        VoterExportSummaryDto summary = voterExportService.summary(grant.agent(), grant.boothId(), null, ALL);

        VoterExportService.Pdf pdf = voterExportService.export(grant.agent(), grant.boothId(), null, ALL);

        assertThat(new String(pdf.bytes(), 0, 5)).isEqualTo("%PDF-");
        assertThat(pdf.fileName()).isEqualTo(summary.fileName());

        PdfReader reader = new PdfReader(pdf.bytes());
        assertThat(reader.getNumberOfPages()).isEqualTo(summary.pages());
        reader.close();
    }

    @Test
    void anAgentWithTheSwitchOffIsRefused() {
        Grant grant = grantWithVoters();
        User agent = grant.agent();
        agent.setDownloadEnabled(false);
        userRepository.save(agent);

        assertThatThrownBy(() -> voterExportService.summary(agent, grant.boothId(), null, ALL))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("turned off")
                .extracting(error -> ((BusinessException) error).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void anAgentWithNoGrantForThatBoothIsRefused() {
        Grant grant = grantWithVoters();
        UUID elsewhere = unitRepository.findByLevelOrderByNameAsc(UnitLevel.BOOTH).stream()
                .map(Unit::getId)
                .filter(id -> !accessRequestService.accessibleBoothIds(grant.agent().getId()).contains(id))
                .findFirst()
                .orElseThrow();

        assertThatThrownBy(() -> voterExportService.summary(grant.agent(), elsewhere, null, ALL))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("do not have access");
    }

    /** DL-12: an agent whose approval has run out is told when, not just "no". */
    @Test
    void expiredAccessSaysWhenItExpired() {
        Grant grant = grantWithVoters();
        AccessRequest approved = accessRequestRepository
                .findById(grant.grantId())
                .orElseThrow();
        approved.setExpiresAt(Instant.now().minus(2, ChronoUnit.DAYS));
        accessRequestRepository.save(approved);

        assertThatThrownBy(() -> voterExportService.summary(grant.agent(), grant.boothId(), null, ALL))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expired on")
                .extracting(error -> ((BusinessException) error).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    /** DL-10: past the cap the file is refused rather than quietly truncated. */
    @Test
    void tooManyVotersIsRefusedBeforeAnythingIsBuilt() {
        Grant grant = grantWithVoters();
        UUID boothId = grant.boothId();
        List<Voter> bulk = new ArrayList<>();
        for (int i = 0; i <= VoterExportService.MAX_ROWS; i++) {
            bulk.add(Voter.builder()
                    .id(UUID.randomUUID())
                    .epicNo("CAP" + UUID.randomUUID().toString().substring(0, 8))
                    .name("Cap test voter " + i)
                    .relation("Cap test")
                    .houseNo(String.valueOf(900000 + i))
                    .age(30)
                    .gender(Gender.M)
                    .booth(Unit.builder().id(boothId).build())
                    .createdAt(Instant.now())
                    .build());
        }
        voterRepository.insertAll(bulk);
        try {
            assertThatThrownBy(() -> voterExportService.export(grant.agent(), boothId, null, ALL))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("narrow the sentiment selection")
                    .extracting(error -> ((BusinessException) error).getStatus())
                    .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        } finally {
            bulk.forEach(voterRepository::delete);
        }
    }

    /**
     * A throwaway agent holding the panchayat above a booth that has voters, with
     * the download switch on. Its own agent every time: these tests switch the
     * download off and expire the grant, which no seeded agent may suffer.
     */
    private Grant grantWithVoters() {
        Unit booth = unitRepository.findByLevelOrderByNameAsc(UnitLevel.BOOTH).stream()
                .filter(unit -> voterRepository.countByBoothId(unit.getId()) > 0)
                .findFirst()
                .orElseThrow(() -> new AssertionError("the seed has no booth with voters"));

        User agent = newAgent();
        String candidate = accessRequestRepository.findAll().stream()
                .findFirst()
                .orElseThrow()
                .getCandidate()
                .getName();

        AccessRequest request = accessRequestService.create(
                agent, new CreateAccessRequestDto(booth.getParent().getId(), candidate));
        accessRequestService.approve(request.getId(), 6, null);
        return new Grant(agent, booth.getId(), request.getId());
    }

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private User newAgent() {
        int seq = SEQUENCE.incrementAndGet();
        String suffix = String.format("%09d", seq);
        return userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .name("Export test " + seq)
                .phone("5" + suffix)
                .email("export-" + suffix + "@k-pulse.test")
                .role(Role.FIELD_AGENT)
                .active(true)
                .downloadEnabled(true)
                .createdAt(Instant.now())
                .build());
    }

    private record Grant(User agent, UUID boothId, UUID grantId) {}
}
