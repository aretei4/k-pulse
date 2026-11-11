package com.kahga.pluse.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.kahga.pluse.candidate.entity.Candidate;
import com.kahga.pluse.candidate.service.CandidateService;
import com.kahga.pluse.electioncycle.service.ElectionCycleService;
import com.kahga.pluse.location.entity.Unit;
import com.kahga.pluse.location.entity.UnitLevel;
import com.kahga.pluse.location.repository.UnitRepository;
import com.kahga.pluse.report.dto.ConfidenceSummaryDto;
import com.kahga.pluse.report.dto.ReportFilterRequest;
import com.kahga.pluse.report.dto.ReportResponse;
import com.kahga.pluse.report.dto.SentimentSummaryDto;
import com.kahga.pluse.report.service.ReportAggregationService;
import com.kahga.pluse.sentiment.entity.ConfidenceLevel;
import com.kahga.pluse.sentiment.entity.SentimentEntry;
import com.kahga.pluse.sentiment.entity.SentimentValue;
import com.kahga.pluse.sentiment.repository.SentimentEntryRepository;
import com.kahga.pluse.user.entity.Role;
import com.kahga.pluse.user.entity.User;
import com.kahga.pluse.user.repository.UserRepository;
import com.kahga.pluse.voter.entity.Voter;
import com.kahga.pluse.voter.repository.VoterRepository;
import com.kahga.pluse.voter.repository.VoterSearchBy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * The Insights screen's residence choice: whether voters the agent found living
 * elsewhere are counted in the split.
 *
 * <p>Each test records against a candidate of its own, so the figures asserted
 * are exactly the ones it wrote and nothing the seed or another test left in the
 * same booth.
 */
@SpringBootTest
@ActiveProfiles("test")
class InsightsResidenceTest {

    /** Of the ten entries each test records, how many name a voter living in the booth. */
    private static final int LIVING = 7;

    private static final int AWAY = 3;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private ReportAggregationService aggregationService;

    @Autowired
    private SentimentEntryRepository sentimentEntryRepository;

    @Autowired
    private CandidateService candidateService;

    @Autowired
    private ElectionCycleService electionCycleService;

    @Autowired
    private UnitRepository unitRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VoterRepository voterRepository;

    /**
     * The whole point of the toggle: the three entries marked as not residing
     * leave the split and the roll together, and nothing else moves.
     */
    @Test
    void theResidenceChoiceMovesOnlyTheEntriesItNames() {
        Recorded recorded = record();

        SentimentSummaryDto everyone = summary(recorded, false);
        SentimentSummaryDto residents = summary(recorded, true);

        // Four of the seven residents were positive; all three living elsewhere were.
        assertThat(everyone.positive()).isEqualTo(7);
        assertThat(residents.positive()).isEqualTo(4);
        assertThat(residents.neutral()).isEqualTo(everyone.neutral()).isEqualTo(2);
        assertThat(residents.negative()).isEqualTo(everyone.negative()).isEqualTo(1);

        // Reported either way, because the screen names the figure in its own hint.
        assertThat(everyone.nonResident()).isEqualTo(AWAY);
        assertThat(residents.nonResident()).isEqualTo(AWAY);

        // A voter already visited is not left to do, whatever was found there.
        assertThat(residents.notRecorded())
                .isEqualTo(everyone.notRecorded())
                .isEqualTo(recorded.voters() - LIVING - AWAY);

        // The roll loses them instead, which is what keeps the four figures adding up.
        assertThat(everyone.total()).isEqualTo(recorded.voters());
        assertThat(residents.total()).isEqualTo(recorded.voters() - AWAY);
        assertThat(addUp(everyone)).isEqualTo(everyone.total());
        assertThat(addUp(residents)).isEqualTo(residents.total());
    }

    /** The donut beside the split is drawn from the same entries, so it follows. */
    @Test
    void theConfidenceDonutFollowsTheSameChoice() {
        Recorded recorded = record();

        ConfidenceSummaryDto everyone = report(recorded, false).confidence();
        ConfidenceSummaryDto residents = report(recorded, true).confidence();

        assertThat(everyone.high()).isEqualTo(LIVING);
        assertThat(everyone.low()).isEqualTo(AWAY);

        assertThat(residents.high()).isEqualTo(LIVING);
        assertThat(residents.low()).isZero();
    }

    private long addUp(SentimentSummaryDto row) {
        return row.positive() + row.neutral() + row.negative() + row.notRecorded();
    }

    private SentimentSummaryDto summary(Recorded recorded, boolean residentsOnly) {
        List<SentimentSummaryDto> rows = report(recorded, residentsOnly).rows();
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    private ReportResponse report(Recorded recorded, boolean residentsOnly) {
        return aggregationService.report(new ReportFilterRequest(
                UnitLevel.BOOTH, recorded.boothId(), recorded.candidateId(), null, null, null, null, residentsOnly));
    }

    /**
     * Ten entries in one booth for a candidate of this test's own: seven voters
     * living there, three found living elsewhere, with the confidence levels set
     * apart so the donut can be told from the split.
     */
    private Recorded record() {
        Unit booth = unitRepository.findByLevelOrderByNameAsc(UnitLevel.BOOTH).stream()
                .filter(unit -> voterRepository.countByBoothId(unit.getId()) >= LIVING + AWAY)
                .findFirst()
                .orElseThrow(() -> new AssertionError("the seed has no booth with enough voters"));

        List<Voter> voters = voterRepository
                .search(null, booth.getId(), null, VoterSearchBy.EPIC_NO, null, false, null, 0, LIVING + AWAY)
                .content();
        assertThat(voters).hasSize(LIVING + AWAY);

        Candidate candidate = candidateService.findOrCreateByName("Residence test " + SEQUENCE.incrementAndGet());
        User agent = userRepository.findByRoleOrderByNameAsc(Role.FIELD_AGENT).stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError("the seed has no field agent"));

        List<SentimentEntry> entries = new ArrayList<>();
        for (int i = 0; i < voters.size(); i++) {
            boolean living = i < LIVING;
            entries.add(SentimentEntry.builder()
                    .id(UUID.randomUUID())
                    .voter(voters.get(i))
                    .candidate(candidate)
                    .sentiment(living ? resident(i) : SentimentValue.POSITIVE)
                    .confidence(living ? ConfidenceLevel.HIGH : ConfidenceLevel.LOW)
                    .resident(living)
                    .wardNo(voters.get(i).getWardNo())
                    .electionCycle(electionCycleService.current())
                    .recordedBy(agent)
                    .recordedAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build());
        }
        sentimentEntryRepository.saveAll(entries);

        return new Recorded(booth.getId(), candidate.getId(), voterRepository.countByBoothId(booth.getId()));
    }

    /** Four positive, two neutral, one negative — a split the toggle visibly changes. */
    private SentimentValue resident(int index) {
        if (index < 4) {
            return SentimentValue.POSITIVE;
        }
        return index < 6 ? SentimentValue.NEUTRAL : SentimentValue.NEGATIVE;
    }

    private record Recorded(UUID boothId, UUID candidateId, long voters) {}
}
