package com.kahga.pluse.electioncycle;

import static org.assertj.core.api.Assertions.assertThat;

import com.kahga.pluse.accessrequest.dto.AgentBoothDto;
import com.kahga.pluse.accessrequest.service.AccessRequestService;
import com.kahga.pluse.electioncycle.dto.CreateElectionCycleRequest;
import com.kahga.pluse.electioncycle.entity.CycleStatus;
import com.kahga.pluse.electioncycle.entity.ElectionCycle;
import com.kahga.pluse.electioncycle.entity.ElectionType;
import com.kahga.pluse.electioncycle.service.ElectionCycleService;
import com.kahga.pluse.housesentiment.dto.RecordHouseSentimentRequest;
import com.kahga.pluse.housesentiment.service.HouseSentimentService;
import com.kahga.pluse.location.entity.UnitLevel;
import com.kahga.pluse.security.UserPrincipal;
import com.kahga.pluse.sentiment.dto.CandidateSentimentFilter;
import com.kahga.pluse.sentiment.dto.SentimentSource;
import com.kahga.pluse.sentiment.entity.ConfidenceLevel;
import com.kahga.pluse.sentiment.service.CandidateSentimentService;
import com.kahga.pluse.user.entity.Role;
import com.kahga.pluse.user.entity.User;
import com.kahga.pluse.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

/**
 * FR-A11. The whole point is that the next campaign does not erase the last
 * one's work, so that is what these assert — not just that a column exists.
 */
@SpringBootTest
@ActiveProfiles("test")
class ElectionCycleTest {

    @Autowired
    private ElectionCycleService electionCycleService;

    @Autowired
    private HouseSentimentService houseSentimentService;

    @Autowired
    private CandidateSentimentService candidateSentimentService;

    @Autowired
    private AccessRequestService accessRequestService;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void signInAsSuperAdmin() {
        User superAdmin = userRepository.findByRoleOrderByNameAsc(Role.SUPER_ADMIN).stream()
                .filter(User::isActive)
                .findFirst()
                .orElseThrow();
        var principal = UserPrincipal.from(superAdmin);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theMigrationBackfillsWhatWasAlreadyRecordedAndLeavesOneCycleOpen() {
        // The cycle the migration created, found by name rather than by "current":
        // other tests in this suite open cycles, and order is not guaranteed.
        ElectionCycle seeded = electionCycleService.list().stream()
                .filter(cycle -> "Cycle 1".equals(cycle.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the migration's cycle is missing"));

        // Sentiment that predates cycles must have been adopted, or the existing
        // campaign's data would simply vanish from every report. Summed over every
        // candidate, because which one holds the seeded entries is not this
        // test's business.
        long seededEntries = candidateSentimentService.candidatesInScope().stream()
                .mapToLong(candidate -> totalFor(candidate.getId(), seeded.getId()))
                .sum();
        assertThat(seededEntries).isPositive();

        assertThat(electionCycleService.list())
                .filteredOn(cycle -> cycle.getStatus() == CycleStatus.OPEN)
                .hasSize(1);
    }

    @Test
    void openingTheNextCycleClosesTheCurrentOne() {
        ElectionCycle previous = electionCycleService.current();

        ElectionCycle opened = electionCycleService.open(
                new CreateElectionCycleRequest("Panchayat 2031", ElectionType.PANCHAYAT, 2031));

        assertThat(opened.getStatus()).isEqualTo(CycleStatus.OPEN);
        assertThat(electionCycleService.require(previous.getId()).getStatus()).isEqualTo(CycleStatus.CLOSED);
        assertThat(electionCycleService.require(previous.getId()).getClosedAt()).isNotNull();
        // Still exactly one open cycle, so "which cycle is this?" has one answer.
        assertThat(electionCycleService.list())
                .filteredOn(cycle -> cycle.getStatus() == CycleStatus.OPEN)
                .hasSize(1);
    }

    @Test
    void whatIsRecordedNextLandsInTheNewCycleAndLeavesTheOldAlone() {
        UUID candidateId = candidateWithData();
        ElectionCycle previous = electionCycleService.current();
        long previousTotal = totalFor(candidateId, previous.getId());

        ElectionCycle fresh = electionCycleService.open(
                new CreateElectionCycleRequest("Panchayat 2036", ElectionType.PANCHAYAT, 2036));
        long freshBefore = totalFor(candidateId, fresh.getId());

        recordSixPeopleInAHouse(candidateId);

        assertThat(totalFor(candidateId, fresh.getId())).isEqualTo(freshBefore + 6);
        // The closed cycle keeps exactly what it had: this is the requirement.
        assertThat(totalFor(candidateId, previous.getId())).isEqualTo(previousTotal);

        // And both sit side by side for comparison.
        var acrossCycles = candidateSentimentService.acrossCycles(
                candidateId, new CandidateSentimentFilter(null, null, SentimentSource.ALL, null, null, null));
        assertThat(acrossCycles).anySatisfy(row -> {
            assertThat(row.cycleId()).isEqualTo(fresh.getId());
            assertThat(row.split().total()).isEqualTo(freshBefore + 6);
        });
        assertThat(acrossCycles).anySatisfy(row -> {
            assertThat(row.cycleId()).isEqualTo(previous.getId());
            assertThat(row.split().total()).isEqualTo(previousTotal);
        });
    }

    private long totalFor(UUID candidateId, UUID cycleId) {
        return candidateSentimentService
                .report(
                        candidateId,
                        new CandidateSentimentFilter(UnitLevel.BOOTH, null, SentimentSource.ALL, cycleId, null, null))
                .totals()
                .total();
    }

    /**
     * Taken from an agent's own grant, not from "first candidate with data":
     * other suites add entries for other candidates, and this test needs one it
     * can actually record against.
     */
    private UUID candidateWithData() {
        return approvedBooth().candidateId();
    }

    private AgentBoothDto approvedBooth() {
        return userRepository.findByRoleOrderByNameAsc(Role.FIELD_AGENT).stream()
                .map(agent -> accessRequestService.accessibleBooths(agent.getId()))
                .filter(booths -> !booths.isEmpty())
                .findFirst()
                .orElseThrow(() -> new AssertionError("seed data has no agent with an approved booth"))
                .get(0);
    }

    private void recordSixPeopleInAHouse(UUID candidateId) {
        User agent = userRepository.findByRoleOrderByNameAsc(Role.FIELD_AGENT).stream()
                .filter(user -> accessRequestService.accessibleBooths(user.getId()).stream()
                        .anyMatch(booth -> booth.candidateId().equals(candidateId)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no agent is approved for that candidate"));
        AgentBoothDto booth = accessRequestService.accessibleBooths(agent.getId()).stream()
                .filter(b -> b.candidateId().equals(candidateId))
                .findFirst()
                .orElseThrow();

        houseSentimentService.record(
                agent,
                new RecordHouseSentimentRequest(
                        booth.boothId(),
                        candidateId,
                        "CYC/" + UUID.randomUUID().toString().substring(0, 4),
                        "Ranjit Nayak",
                        4,
                        6,
                        6,
                        4,
                        1,
                        1,
                        ConfidenceLevel.HIGH));
    }
}
