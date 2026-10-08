package com.kahga.pluse.sentiment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.kahga.pluse.accessrequest.dto.AgentBoothDto;
import com.kahga.pluse.accessrequest.service.AccessRequestService;
import com.kahga.pluse.candidate.repository.CandidateRepository;
import com.kahga.pluse.housesentiment.dto.RecordHouseSentimentRequest;
import com.kahga.pluse.housesentiment.service.HouseSentimentService;
import com.kahga.pluse.location.entity.Unit;
import com.kahga.pluse.location.entity.UnitLevel;
import com.kahga.pluse.location.repository.UnitRepository;
import com.kahga.pluse.location.service.LocationService;
import com.kahga.pluse.security.UserPrincipal;
import com.kahga.pluse.sentiment.dto.CandidateSentimentDto;
import com.kahga.pluse.sentiment.dto.CandidateSentimentDto.Split;
import com.kahga.pluse.sentiment.dto.CandidateSentimentDto.Status;
import com.kahga.pluse.sentiment.dto.CandidateSentimentDto.Verdict;
import com.kahga.pluse.sentiment.dto.CandidateSentimentFilter;
import com.kahga.pluse.sentiment.dto.SentimentSource;
import com.kahga.pluse.sentiment.entity.ConfidenceLevel;
import com.kahga.pluse.sentiment.service.CandidateSentimentService;
import com.kahga.pluse.user.entity.AccessScope;
import com.kahga.pluse.user.entity.Role;
import com.kahga.pluse.user.entity.User;
import com.kahga.pluse.user.repository.UserRepository;
import java.time.Instant;
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
 * FR-A15. The verdict rule decides what an admin believes about a candidate, so
 * each branch of it is asserted here rather than left to the screen.
 */
@SpringBootTest
@ActiveProfiles("test")
class CandidateSentimentTest {

    @Autowired
    private CandidateSentimentService candidateSentimentService;

    @Autowired
    private HouseSentimentService houseSentimentService;

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private com.kahga.pluse.candidate.service.CandidateService candidateService;

    @Autowired
    private AccessRequestService accessRequestService;

    @Autowired
    private LocationService locationService;

    @Autowired
    private UnitRepository unitRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void signInAsSuperAdmin() {
        signIn(userRepository.findByRoleOrderByNameAsc(Role.SUPER_ADMIN).stream()
                .filter(User::isActive)
                .findFirst()
                .orElseThrow());
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void netLeadIsPositiveShareMinusNegativeShareAndNeutralsOnlyDilute() {
        Split split = Split.of(50, 10, 40);

        assertThat(split.total()).isEqualTo(100);
        assertThat(split.netLead()).isCloseTo(10.0, within(0.001));
        assertThat(split.verdict()).isEqualTo(Verdict.POSITIVE);

        // Same lead, more neutrals: the gap narrows because both shares shrink.
        Split diluted = Split.of(50, 100, 40);
        assertThat(diluted.netLead()).isLessThan(split.netLead());
    }

    @Test
    void anExactTieReadsNegativeAndNothingRecordedReadsNoData() {
        assertThat(Split.of(40, 20, 40).verdict()).isEqualTo(Verdict.NEGATIVE);
        // The documented edge case: no data must not look like a candidate doing badly.
        assertThat(Split.of(0, 0, 0).verdict()).isEqualTo(Verdict.NO_DATA);
        assertThat(Split.of(0, 0, 0).status(20, 5)).isEqualTo(Status.NO_DATA);
    }

    @Test
    void boothStatusFollowsTheDocumentedThresholds() {
        assertThat(Split.of(60, 0, 40).status(20, 5)).isEqualTo(Status.SAFE); // +20 exactly
        assertThat(Split.of(59, 0, 41).status(20, 5)).isEqualTo(Status.WATCH); // +18
        assertThat(Split.of(105, 0, 95).status(20, 5)).isEqualTo(Status.WATCH); // +5 exactly, the lower bound
        assertThat(Split.of(52, 0, 48).status(20, 5)).isEqualTo(Status.AT_RISK); // +4, just under
        assertThat(Split.of(51, 0, 49).status(20, 5)).isEqualTo(Status.AT_RISK); // +2
        assertThat(Split.of(30, 0, 70).status(20, 5)).isEqualTo(Status.AT_RISK);
    }

    @Test
    void theJsonCarriesEveryFigureTheScreenReads() throws Exception {
        // A record only serialises its components, so a derived value written as
        // a helper method silently vanishes from the response. This asserts the
        // wire shape, which no amount of in-process testing would have caught.
        var report = candidateSentimentService.report(candidateWithData(), filter(UnitLevel.BOOTH, null));
        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(report));

        var totals = json.get("totals");
        assertThat(totals.has("total")).isTrue();
        assertThat(totals.has("positivePercent")).isTrue();
        assertThat(totals.has("neutralPercent")).isTrue();
        assertThat(totals.has("negativePercent")).isTrue();
        assertThat(totals.has("netLead")).isTrue();
        assertThat(totals.has("verdict")).isTrue();
        assertThat(totals.get("total").asLong()).isEqualTo(report.totals().total());

        var row = json.get("rows").get(0);
        assertThat(row.has("netLead")).isTrue();
        assertThat(row.has("status")).isTrue();
        assertThat(row.has("drillable")).isTrue();
        assertThat(row.get("split").has("total")).isTrue();
    }

    @Test
    void aCandidateMappedToAPanchayatOpensOnThatPanchayatsBooths() {
        // A candidate stands in one panchayat, so the screen should open there
        // rather than at a district the admin has to drill through twice.
        UUID candidateId = candidateWithData();
        Unit panchayat = panchayatWithData(candidateId);
        candidateService.update(
                candidateId,
                new com.kahga.pluse.candidate.dto.CandidateRequest(
                        candidateRepository.findById(candidateId).orElseThrow().getName(), null, panchayat.getId()));

        // No level, no parent: the default has to come from the candidate itself.
        var report = candidateSentimentService.report(
                candidateId, new CandidateSentimentFilter(null, null, SentimentSource.ALL, null, null, null));

        assertThat(report.candidateUnitId()).isEqualTo(panchayat.getId());
        assertThat(report.candidateUnitName()).isEqualTo(panchayat.getName());
        assertThat(report.level()).isEqualTo(UnitLevel.BOOTH);
        assertThat(report.parentUnitId()).isEqualTo(panchayat.getId());

        var boothsThere = locationService.boothIdsUnder(panchayat.getId());
        assertThat(report.rows()).isNotEmpty();
        assertThat(report.rows()).allSatisfy(row -> {
            assertThat(row.unitLevel()).isEqualTo(UnitLevel.BOOTH);
            assertThat(boothsThere).contains(row.unitId());
        });

        // Asking for something wider still works — the default is a starting
        // point, not a cage.
        var wider = candidateSentimentService.report(
                candidateId, new CandidateSentimentFilter(UnitLevel.DISTRICT, null, SentimentSource.ALL, null, null, null));
        assertThat(wider.level()).isEqualTo(UnitLevel.DISTRICT);
    }

    @Test
    void theOverallTotalIsTheSumOfItsRows() {
        UUID candidateId = candidateWithData();
        CandidateSentimentDto report = candidateSentimentService.report(candidateId, filter(UnitLevel.BOOTH, null));

        long positive = report.rows().stream().mapToLong(r -> r.split().positive()).sum();
        long negative = report.rows().stream().mapToLong(r -> r.split().negative()).sum();

        assertThat(report.totals().positive()).isEqualTo(positive);
        assertThat(report.totals().negative()).isEqualTo(negative);
        assertThat(report.totals().netLead())
                .isCloseTo(report.totals().positivePercent() - report.totals().negativePercent(), within(0.001));
        assertThat(report.rows()).allSatisfy(row -> assertThat(row.drillable()).isFalse());
    }

    @Test
    void houseTalliesAreWeightedByPeopleAndCanBeExcluded() {
        UUID candidateId = candidateWithData();
        CandidateSentimentDto voterOnlyBefore =
                candidateSentimentService.report(candidateId, filter(UnitLevel.BOOTH, null, SentimentSource.VOTER));
        CandidateSentimentDto combinedBefore =
                candidateSentimentService.report(candidateId, filter(UnitLevel.BOOTH, null, SentimentSource.ALL));

        recordSixPositivePeopleInAHouse(candidateId);

        CandidateSentimentDto voterOnlyAfter =
                candidateSentimentService.report(candidateId, filter(UnitLevel.BOOTH, null, SentimentSource.VOTER));
        CandidateSentimentDto combinedAfter =
                candidateSentimentService.report(candidateId, filter(UnitLevel.BOOTH, null, SentimentSource.ALL));

        // One house of six counts as six, not as one.
        assertThat(combinedAfter.totals().positive()).isEqualTo(combinedBefore.totals().positive() + 6);
        // The named-voter view is untouched by it.
        assertThat(voterOnlyAfter.totals().positive()).isEqualTo(voterOnlyBefore.totals().positive());
    }

    @Test
    void aScopedAdminSeesOnlyTheirOwnBooths() {
        UUID candidateId = candidateWithData();
        Unit panchayat = panchayatWithData(candidateId);
        signIn(scopedAdmin(panchayat));

        CandidateSentimentDto report = candidateSentimentService.report(candidateId, filter(UnitLevel.BOOTH, null));
        var mine = locationService.boothIdsUnder(panchayat.getId());

        assertThat(report.rows()).isNotEmpty();
        assertThat(report.rows()).allSatisfy(row -> assertThat(mine).contains(row.unitId()));
    }

    @Test
    void theDropdownOffersOnlyCandidatesWithSomethingRecordedInScope() {
        var everyCandidate = candidateRepository.findAllByOrderByNameAsc();
        var offeredToSuperAdmin = candidateSentimentService.candidatesInScope();

        assertThat(offeredToSuperAdmin).isNotEmpty();
        // Never more than exist, and only ones that actually have data.
        assertThat(offeredToSuperAdmin.size()).isLessThanOrEqualTo(everyCandidate.size());
        assertThat(offeredToSuperAdmin).allSatisfy(candidate -> assertThat(candidateSentimentService
                        .report(candidate.getId(), filter(UnitLevel.BOOTH, null))
                        .totals()
                        .total())
                .isPositive());

        // An admin over a unit that does have entries still sees the candidate.
        signIn(scopedAdmin(panchayatWithData(offeredToSuperAdmin.get(0).getId())));
        assertThat(candidateSentimentService.candidatesInScope()).isNotEmpty();
    }

    /** A panchayat that actually holds entries, so "scoped" is tested against real numbers. */
    private Unit panchayatWithData(UUID candidateId) {
        UUID boothId = candidateSentimentService.report(candidateId, filter(UnitLevel.BOOTH, null)).rows().stream()
                .filter(row -> row.split().total() > 0)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no booth has sentiment for that candidate"))
                .unitId();
        return locationService.require(locationService.require(boothId).getParent().getId());
    }

    private UUID candidateWithData() {
        return candidateSentimentService.candidatesInScope().stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError("seed data has no candidate with sentiment"))
                .getId();
    }

    private void recordSixPositivePeopleInAHouse(UUID candidateId) {
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
                        "CS/" + UUID.randomUUID().toString().substring(0, 4),
                        "Bijay Kumar Behera",
                        4,
                        6,
                        6,
                        6,
                        0,
                        0,
                        ConfidenceLevel.HIGH));
    }

    private CandidateSentimentFilter filter(UnitLevel level, UUID parentUnitId) {
        return filter(level, parentUnitId, SentimentSource.ALL);
    }

    private CandidateSentimentFilter filter(UnitLevel level, UUID parentUnitId, SentimentSource source) {
        return new CandidateSentimentFilter(level, parentUnitId, source, null, null, null);
    }

    private User scopedAdmin(Unit unit) {
        return userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .name("Candidate view admin " + UUID.randomUUID().toString().substring(0, 6))
                .email("cand-view-" + UUID.randomUUID().toString().substring(0, 6) + "@k-pulse.test")
                .passwordHash("x")
                .role(Role.ADMIN)
                .scope(AccessScope.builder().level(unit.getLevel()).unit(unit).build())
                .active(true)
                .createdAt(Instant.now())
                .build());
    }

    private void signIn(User user) {
        var principal = UserPrincipal.from(user);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
