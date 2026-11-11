package com.kahga.pluse.agentreport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kahga.pluse.accessrequest.entity.AccessRequest;
import com.kahga.pluse.accessrequest.repository.AccessRequestRepository;
import com.kahga.pluse.accessrequest.service.AccessRequestService;
import com.kahga.pluse.agentreport.dto.AgentReportDto;
import com.kahga.pluse.agentreport.dto.AgentReportScopeDto;
import com.kahga.pluse.agentreport.service.AgentReportService;
import com.kahga.pluse.common.exception.BusinessException;
import com.kahga.pluse.fieldagent.service.FieldAgentService;
import com.kahga.pluse.location.entity.UnitLevel;
import com.kahga.pluse.location.repository.UnitRepository;
import com.kahga.pluse.location.service.LocationService;
import com.kahga.pluse.security.UserPrincipal;
import com.kahga.pluse.user.entity.Role;
import com.kahga.pluse.user.entity.User;
import com.kahga.pluse.candidate.service.CandidateService;
import com.kahga.pluse.electioncycle.service.ElectionCycleService;
import com.kahga.pluse.sentiment.entity.ConfidenceLevel;
import com.kahga.pluse.sentiment.entity.SentimentEntry;
import com.kahga.pluse.sentiment.entity.SentimentValue;
import com.kahga.pluse.sentiment.repository.SentimentEntryRepository;
import com.kahga.pluse.user.repository.UserRepository;
import com.kahga.pluse.voter.entity.Voter;
import com.kahga.pluse.voter.repository.VoterRepository;
import com.kahga.pluse.voter.repository.VoterSearchBy;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-U14's access rules, which are the whole point of the feature: the page
 * exists only when an admin gives it, and it never reaches past the booths the
 * agent was already approved for.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AgentReportAccessTest {

    @Autowired
    private AgentReportService agentReportService;

    @Autowired
    private FieldAgentService fieldAgentService;

    @Autowired
    private AccessRequestService accessRequestService;

    @Autowired
    private AccessRequestRepository accessRequestRepository;

    @Autowired
    private LocationService locationService;

    @Autowired
    private UnitRepository unitRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VoterRepository voterRepository;

    @Autowired
    private SentimentEntryRepository sentimentEntryRepository;

    @Autowired
    private CandidateService candidateService;

    @Autowired
    private ElectionCycleService electionCycleService;

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

    /** AR-1 and acceptance criterion 1. */
    @Test
    void anAgentWithoutTheSwitchIsRefusedEveryReportsCall() {
        User agent = agentWithGrant();
        assertThat(agent.isReportsEnabled()).as("off by default, AD-2").isFalse();

        assertThatThrownBy(() -> agentReportService.scope(agent))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not switched on");
        assertThatThrownBy(() -> agentReportService.report(agent, UUID.randomUUID(), null, null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not switched on");
    }

    /** Acceptance criterion 2: the admin gives it, and the scope is their own booths. */
    @Test
    void afterTheAdminGivesAccessTheScopeIsTheAgentsOwnApprovedBooths() {
        User agent = agentWithGrant();
        fieldAgentService.updateReportsAccess(agent.getId(), true);

        List<AgentReportScopeDto> scope = agentReportService.scope(reload(agent));

        assertThat(scope).isNotEmpty();
        AgentReportScopeDto first = scope.get(0);
        assertThat(first.panchayatName()).isNotBlank();
        assertThat(first.candidateName()).isNotBlank();
        assertThat(first.booths()).isNotEmpty();

        Set<UUID> approved = approvedBoothIds(agent);
        assertThat(first.booths())
                .extracting(AgentReportScopeDto.BoothRef::id)
                .allMatch(approved::contains);
    }

    /** AR-5: the figures are the agent's booths, never the whole panchayat. */
    @Test
    void theReportCoversOnlyTheApprovedBoothsAndAddsUp() {
        User agent = agentWithGrant();
        fieldAgentService.updateReportsAccess(agent.getId(), true);
        AgentReportScopeDto scope = agentReportService.scope(reload(agent)).get(0);

        // Recorded here rather than relied on from the seed: other tests in this
        // suite delete accounts and the entries that go with them, and a report
        // test that depended on what they left behind would pass or fail by
        // running order.
        long recorded = recordThreeEntries(scope);

        AgentReportDto report = agentReportService.report(
                reload(agent), scope.panchayatId(), scope.candidateId(), null, null, null, null, null);

        assertThat(report.booths()).hasSize(scope.booths().size());
        assertThat(report.booths())
                .extracting(AgentReportDto.BoothRow::boothId)
                .containsExactlyInAnyOrderElementsOf(
                        scope.booths().stream().map(AgentReportScopeDto.BoothRef::id).toList());

        assertThat(report.totals().voters())
                .isEqualTo(report.booths().stream()
                        .mapToLong(AgentReportDto.BoothRow::voters)
                        .sum());
        assertThat(report.totals().positive())
                .isEqualTo(report.booths().stream()
                        .mapToLong(AgentReportDto.BoothRow::positive)
                        .sum());
        assertThat(report.totals().notRecorded())
                .isEqualTo(report.booths().stream()
                        .mapToLong(AgentReportDto.BoothRow::notRecorded)
                        .sum());
        assertThat(report.totals().recorded())
                .isGreaterThanOrEqualTo(recorded)
                .isLessThanOrEqualTo(report.totals().voters());
        assertThat(report.totals().notRecorded()).isLessThanOrEqualTo(report.totals().voters());
    }

    /** Three voters of the first approved booth, one of each sentiment. */
    private long recordThreeEntries(AgentReportScopeDto scope) {
        UUID boothId = scope.booths().get(0).id();
        List<Voter> roll = voterRepository
                .search(null, boothId, null, VoterSearchBy.EPIC_NO, null, false, null, 0, 3)
                .content();
        assertThat(roll).hasSize(3);

        User recorder = userRepository.findByRoleOrderByNameAsc(Role.FIELD_AGENT).stream()
                .findFirst()
                .orElseThrow();
        SentimentValue[] values = {SentimentValue.POSITIVE, SentimentValue.NEUTRAL, SentimentValue.NEGATIVE};
        for (int i = 0; i < roll.size(); i++) {
            sentimentEntryRepository.save(SentimentEntry.builder()
                    .id(UUID.randomUUID())
                    .voter(roll.get(i))
                    .candidate(candidateService.require(scope.candidateId()))
                    .sentiment(values[i])
                    .confidence(ConfidenceLevel.HIGH)
                    .resident(true)
                    .wardNo(roll.get(i).getWardNo())
                    .electionCycle(electionCycleService.current())
                    .recordedBy(recorder)
                    .recordedAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build());
        }
        return roll.size();
    }

    /** The booth choice narrows the report, and never widens it. */
    @Test
    void theBoothChoiceNarrowsTheReportAndRefusesABoothTheAgentDoesNotHold() {
        User agent = agentWithGrant();
        fieldAgentService.updateReportsAccess(agent.getId(), true);
        AgentReportScopeDto scope = agentReportService.scope(reload(agent)).get(0);

        AgentReportDto all =
                agentReportService.report(reload(agent), scope.panchayatId(), scope.candidateId(), null, null, null, null, null);
        UUID first = scope.booths().get(0).id();

        AgentReportDto one = agentReportService.report(
                reload(agent), scope.panchayatId(), scope.candidateId(), List.of(first), null, null, null, null);

        assertThat(one.booths()).hasSize(1);
        assertThat(one.booths().get(0).boothId()).isEqualTo(first);
        assertThat(one.totals().voters()).isLessThanOrEqualTo(all.totals().voters());

        UUID elsewhere = unitRepository.findByLevelOrderByNameAsc(UnitLevel.BOOTH).stream()
                .map(unit -> unit.getId())
                .filter(id -> scope.booths().stream().noneMatch(booth -> booth.id().equals(id)))
                .findFirst()
                .orElseThrow();
        assertThatThrownBy(() -> agentReportService.report(
                        reload(agent), scope.panchayatId(), scope.candidateId(), List.of(elsewhere), null, null, null,
                        null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no approved access to that booth");
    }

    /**
     * The caste choice narrows the roll, so the recorded and not-recorded
     * figures move together rather than one of them drifting.
     */
    @Test
    void theCasteChoiceNarrowsTheRollAndTheCountsTogether() {
        User agent = agentWithGrant();
        fieldAgentService.updateReportsAccess(agent.getId(), true);
        AgentReportScopeDto scope = agentReportService.scope(reload(agent)).get(0);

        AgentReportDto everyone = agentReportService.report(
                reload(agent), scope.panchayatId(), scope.candidateId(), null, null, null, null, null);

        long byGroup = 0;
        for (String group : new String[] {"GENERAL", "SEBC", "SC", "ST", "MINORITY", "OTHER", "NONE"}) {
            AgentReportDto slice = agentReportService.report(
                    reload(agent), scope.panchayatId(), scope.candidateId(), null, null, null, List.of(group), null);
            assertThat(slice.totals().voters()).isLessThanOrEqualTo(everyone.totals().voters());
            byGroup += slice.totals().voters();
        }
        assertThat(byGroup)
                .as("every voter falls in exactly one group, including those with none recorded")
                .isEqualTo(everyone.totals().voters());
    }

    /** AG-9 / AR-6: the raw-data download needs the second switch as well. */
    @Test
    void theVoterDownloadNeedsBothTheReportsPageAndTheDownloadSwitch() {
        User agent = agentWithGrant();
        fieldAgentService.updateReportsAccess(agent.getId(), true);
        AgentReportScopeDto scope = agentReportService.scope(reload(agent)).get(0);
        recordThreeEntries(scope);

        User withoutDownload = reload(agent);
        withoutDownload.setDownloadEnabled(false);
        userRepository.save(withoutDownload);

        assertThatThrownBy(() -> agentReportService.exportVoters(
                        reload(agent), scope.panchayatId(), scope.candidateId(), null, null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("turned off for you");

        User withDownload = reload(agent);
        withDownload.setDownloadEnabled(true);
        userRepository.save(withDownload);

        var pdf = agentReportService.exportVoters(
                reload(agent), scope.panchayatId(), scope.candidateId(), null, null, null, null, null, null);

        assertThat(pdf.fileName()).endsWith(".pdf").contains("voter-details");
        assertThat(pdf.bytes()).isNotEmpty();
        assertThat(new String(pdf.bytes(), java.nio.charset.StandardCharsets.ISO_8859_1)).startsWith("%PDF");
    }

    /** The file follows the same choices as the figures (RA-N2). */
    @Test
    void theVoterDownloadFollowsTheSentimentChoice() {
        User agent = agentWithGrant();
        fieldAgentService.updateReportsAccess(agent.getId(), true);
        User enabled = reload(agent);
        enabled.setDownloadEnabled(true);
        userRepository.save(enabled);

        AgentReportScopeDto scope = agentReportService.scope(reload(agent)).get(0);
        recordThreeEntries(scope);

        var everything = agentReportService.exportVoters(
                reload(agent), scope.panchayatId(), scope.candidateId(), null, null, null, null, null, null);
        var positiveOnly = agentReportService.exportVoters(
                reload(agent),
                scope.panchayatId(),
                scope.candidateId(),
                null,
                List.of(com.kahga.pluse.voterexport.dto.SentimentFilter.POSITIVE),
                null,
                null,
                null,
                null);

        assertThat(positiveOnly.bytes().length)
                .as("one sentiment is fewer rows than every voter in the booth")
                .isLessThan(everything.bytes().length);
    }

    /** AR-4 and criterion 3: a panchayat the agent has no approval for is refused. */
    @Test
    void aPanchayatTheAgentDoesNotHoldIsRefused() {
        User agent = agentWithGrant();
        fieldAgentService.updateReportsAccess(agent.getId(), true);
        AgentReportScopeDto scope = agentReportService.scope(reload(agent)).get(0);

        UUID elsewhere = unitRepository.findByLevelOrderByNameAsc(UnitLevel.PANCHAYAT).stream()
                .map(unit -> unit.getId())
                .filter(id -> !id.equals(scope.panchayatId()))
                .findFirst()
                .orElseThrow();

        assertThatThrownBy(() ->
                        agentReportService.report(
                                reload(agent), elsewhere, scope.candidateId(), null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no approved access");
    }

    /** AR-2 and criterion 4: an approval that lapses takes its booths with it. */
    @Test
    void anExpiredApprovalLeavesTheReport() {
        User agent = agentWithGrant();
        fieldAgentService.updateReportsAccess(agent.getId(), true);
        assertThat(agentReportService.scope(reload(agent))).isNotEmpty();

        for (AccessRequest grant : accessRequestService.liveGrants(agent.getId())) {
            grant.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
            accessRequestRepository.save(grant);
        }

        assertThat(agentReportService.scope(reload(agent)))
                .as("no live approval means no booths to report on")
                .isEmpty();
    }

    /** AD-5: removing access stops the next call, not the one after a sign-out. */
    @Test
    void removingAccessRefusesTheVeryNextCall() {
        User agent = agentWithGrant();
        fieldAgentService.updateReportsAccess(agent.getId(), true);
        assertThat(agentReportService.scope(reload(agent))).isNotEmpty();

        fieldAgentService.updateReportsAccess(agent.getId(), false);

        assertThatThrownBy(() -> agentReportService.scope(reload(agent)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not switched on");
    }

    private Set<UUID> approvedBoothIds(User agent) {
        return accessRequestService.accessibleBoothIds(agent.getId());
    }

    /** A seeded agent holding a live approval, which is what the page needs. */
    private User agentWithGrant() {
        return userRepository.findByRoleOrderByNameAsc(Role.FIELD_AGENT).stream()
                .filter(user -> !accessRequestService.liveGrants(user.getId()).isEmpty())
                .filter(user -> !locationService
                        .boothIdsUnder(accessRequestService
                                .liveGrants(user.getId())
                                .get(0)
                                .getUnit()
                                .getId())
                        .isEmpty())
                .findFirst()
                .orElseThrow(() -> new AssertionError("the seed has no agent with a live approval"));
    }

    private User reload(User agent) {
        return userRepository.findById(agent.getId()).orElseThrow();
    }

    private void signIn(User user) {
        var principal = UserPrincipal.from(user);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
