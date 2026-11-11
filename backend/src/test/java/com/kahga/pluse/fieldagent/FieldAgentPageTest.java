package com.kahga.pluse.fieldagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kahga.pluse.common.exception.BusinessException;
import com.kahga.pluse.fieldagent.dto.AgentAccessState;
import com.kahga.pluse.fieldagent.dto.AreaCountDto;
import com.kahga.pluse.fieldagent.dto.FieldAgentDto;
import com.kahga.pluse.fieldagent.dto.FieldAgentFilter;
import com.kahga.pluse.fieldagent.dto.FieldAgentPageDto;
import com.kahga.pluse.fieldagent.dto.FieldAgentSort;
import com.kahga.pluse.fieldagent.service.FieldAgentCsv;
import com.kahga.pluse.fieldagent.service.FieldAgentService;
import com.kahga.pluse.location.entity.Unit;
import com.kahga.pluse.location.entity.UnitLevel;
import com.kahga.pluse.location.repository.UnitRepository;
import com.kahga.pluse.security.UserPrincipal;
import com.kahga.pluse.user.entity.AccessScope;
import com.kahga.pluse.user.entity.Role;
import com.kahga.pluse.user.entity.User;
import com.kahga.pluse.user.repository.UserRepository;
import java.util.Collection;
import java.util.List;
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
 * FR-A20: the list behind the Field agents page — paging, the geography and
 * access filters, search, sorting, the tab counts and the export.
 *
 * <p>Asserted against the seeded agents, relatively rather than by hard
 * numbers wherever the seed could grow.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FieldAgentPageTest {

    @Autowired
    private FieldAgentService fieldAgentService;

    @Autowired
    private FieldAgentCsv fieldAgentCsv;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UnitRepository unitRepository;

    @BeforeEach
    void signInAsSuperAdmin() {
        signIn(superAdmin());
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void thePageCarriesTheRowsTheirAreaAndWhatEachHasRecorded() {
        FieldAgentPageDto page = fieldAgentService.page(filter(null, null, 0, 25));

        assertThat(page.total()).isEqualTo(agentCount());
        assertThat(page.items()).isNotEmpty();
        assertThat(page.items()).allSatisfy(agent -> {
            assertThat(agent.agentCode()).matches("A-\\d{5}");
            assertThat(agent.access()).isNotNull();
            assertThat(agent.votersRecorded()).isNotNegative();
        });
        // The seed gives one agent an approved grant and work recorded under it.
        assertThat(page.items())
                .anySatisfy(agent -> {
                    assertThat(agent.access()).isEqualTo(AgentAccessState.APPROVED);
                    assertThat(agent.districtName()).isNotBlank();
                    assertThat(agent.candidateName()).isNotBlank();
                });
        assertThat(page.items()).anySatisfy(agent -> assertThat(agent.votersRecorded()).isPositive());
    }

    /** FA-2: the page is cut by the server, and the cut never repeats a row. */
    @Test
    void pagingReturnsEachAgentExactlyOnce() {
        long total = fieldAgentService.page(filter(null, null, 0, 25)).total();
        List<UUID> seen = new java.util.ArrayList<>();
        for (int page = 0; page * 2 < total; page++) {
            FieldAgentPageDto slice = fieldAgentService.page(filter(null, null, page, 2));
            assertThat(slice.items()).hasSizeLessThanOrEqualTo(2);
            slice.items().forEach(agent -> seen.add(agent.id()));
        }
        assertThat(seen).hasSize((int) total).doesNotHaveDuplicates();
    }

    /** FA-7: whatever else is filtered, the two tabs add up to the first. */
    @Test
    void thePaidAndUnpaidTabsAddUpToAll() {
        FieldAgentPageDto all = fieldAgentService.page(filter(null, null, 0, 25));

        assertThat(all.paidCount() + all.unpaidCount()).isEqualTo(all.allCount());
        assertThat(all.allCount()).isEqualTo(all.total());

        // The All count does not move when the admin switches to a tab; only the
        // page's own total does.
        FieldAgentPageDto unpaid = fieldAgentService.page(filter(null, false, 0, 25));
        assertThat(unpaid.allCount()).isEqualTo(all.allCount());
        assertThat(unpaid.total()).isEqualTo(all.unpaidCount());
    }

    /** FA-3: each level narrows the one above it. */
    @Test
    void choosingADistrictThenABlockNarrowsTheCount() {
        List<AreaCountDto> districts = fieldAgentService.areaCounts(UnitLevel.DISTRICT, null);
        assertThat(districts).isNotEmpty();
        AreaCountDto district = districts.stream()
                .filter(option -> option.agents() > 0)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no district in the seed has an agent"));

        long everywhere = fieldAgentService.page(filter(null, null, 0, 25)).total();
        long inDistrict = fieldAgentService
                .page(filter(fieldAgentService.areaUnitIds(district.unitId(), null, null), null, 0, 25))
                .total();

        assertThat(inDistrict).isEqualTo(district.agents()).isLessThanOrEqualTo(everywhere).isPositive();

        List<AreaCountDto> blocks = fieldAgentService.areaCounts(UnitLevel.BLOCK, district.unitId());
        assertThat(blocks).isNotEmpty();
        assertThat(blocks.stream().mapToLong(AreaCountDto::agents).max().orElse(0))
                .as("a block cannot hold more agents than its district")
                .isLessThanOrEqualTo(inDistrict);
    }

    /** FA-5: an agent whose only grant has lapsed is Expired, not Approved. */
    @Test
    void theAccessFilterSplitsTheAgentsWithoutLosingAny() {
        long total = fieldAgentService.page(filter(null, null, 0, 25)).total();
        long byState = 0;
        for (AgentAccessState state : AgentAccessState.values()) {
            byState += fieldAgentService
                    .page(new FieldAgentFilter(scope(), state, null, null, null, FieldAgentSort.NAME, false, 0, 25))
                    .total();
        }
        assertThat(byState).isEqualTo(total);
    }

    /** FA-6: one box, four things it can match. */
    @Test
    void searchMatchesNameMobileAgentIdOrReferral() {
        FieldAgentDto someone = fieldAgentService.page(filter(null, null, 0, 1)).items().get(0);

        assertThat(search(someone.name())).extracting(FieldAgentDto::id).contains(someone.id());
        assertThat(search(someone.phone())).extracting(FieldAgentDto::id).contains(someone.id());
        assertThat(search(someone.agentCode())).extracting(FieldAgentDto::id).containsExactly(someone.id());
        assertThat(search("no such agent anywhere")).isEmpty();
    }

    /**
     * An admin holding one worker's number wants every agent that number sent.
     * Two agents share a referral here, so this asserts the search finds the
     * group rather than only the one row that happens to be first.
     */
    @Test
    void searchingAReferralNumberFindsEveryoneThatNumberSent() {
        List<FieldAgentDto> two = fieldAgentService.page(filter(null, null, 0, 2)).items();
        assertThat(two).hasSize(2);
        // Nobody's own mobile number, so what comes back is the referral alone.
        String referral = "9771000011";
        two.forEach(agent -> fieldAgentService.updateReferral(agent.id(), referral));

        assertThat(search(referral))
                .extracting(FieldAgentDto::id)
                .containsExactlyInAnyOrder(two.get(0).id(), two.get(1).id());
        assertThat(search("9771000022")).doesNotContain(two.get(0), two.get(1));

        // One box over several columns: a referral that is also an agent's own
        // number finds that agent as well. Useful rather than surprising — the
        // worker and the agents they sent are what the admin is looking for.
        String ownNumber = fieldAgentService
                .page(filter(null, null, 0, 100))
                .items()
                .stream()
                // Somebody else's number: an agent cannot be their own referral.
                .filter(agent -> !agent.id().equals(two.get(0).id()))
                .map(FieldAgentDto::phone)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseThrow();
        fieldAgentService.updateReferral(two.get(0).id(), ownNumber);
        assertThat(search(ownNumber)).extracting(FieldAgentDto::id).contains(two.get(0).id());
    }

    /** FA-8: the server sorts, and reversing it reverses the page. */
    @Test
    void sortingIsDoneByTheServerAndReverses() {
        List<String> up = fieldAgentService.page(filter(null, null, 0, 100)).items().stream()
                .map(FieldAgentDto::name)
                .toList();
        List<String> down = fieldAgentService
                .page(new FieldAgentFilter(scope(), null, null, null, null, FieldAgentSort.NAME, true, 0, 100))
                .items()
                .stream()
                .map(FieldAgentDto::name)
                .toList();

        assertThat(up).isSorted();
        List<String> backwards = new java.util.ArrayList<>(up);
        java.util.Collections.reverse(backwards);
        assertThat(down).containsExactlyElementsOf(backwards);

        List<Long> byVoters = fieldAgentService
                .page(new FieldAgentFilter(scope(), null, null, null, null, FieldAgentSort.VOTERS, true, 0, 100))
                .items()
                .stream()
                .map(FieldAgentDto::votersRecorded)
                .toList();
        assertThat(byVoters).isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    /** FA-4, FA-N3: a scoped admin sees their own area and is refused the rest. */
    @Test
    void aScopedAdminSeesOnlyTheirOwnAreaAndCannotReadOutsideIt() {
        List<FieldAgentDto> everyone = fieldAgentService.page(filter(null, null, 0, 100)).items();
        FieldAgentDto inside = everyone.stream()
                .filter(agent -> agent.areaUnitId() != null)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no seeded agent holds a grant"));
        FieldAgentDto outside = everyone.stream()
                .filter(agent -> agent.areaUnitId() == null || !agent.areaUnitId().equals(inside.areaUnitId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the seed has no second area"));

        signIn(adminScopedTo(unitRepository.findById(inside.areaUnitId()).orElseThrow()));

        List<FieldAgentDto> visible = fieldAgentService.page(filter(null, null, 0, 100)).items();
        assertThat(visible).extracting(FieldAgentDto::id).contains(inside.id()).doesNotContain(outside.id());

        assertThatThrownBy(() -> fieldAgentService.require(outside.id()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("outside your area");
    }

    /** FA-16, §7: the agreed columns, one row per matching agent. */
    @Test
    void theExportHoldsTheFilteredAgentsAndTheAgreedColumns() {
        List<FieldAgentDto> agents = fieldAgentService.allMatching(filter(null, null, 0, 100));

        String csv = fieldAgentCsv.build(agents);
        List<String> lines = csv.lines().toList();

        assertThat(lines.get(0))
                .startsWith("﻿Agent ID,Name,Mobile,Email,Address,Referral,District,Block,Panchayat,Candidate")
                .endsWith("Paid,Amount,Paid on,Mode,Reference");
        assertThat(lines).hasSize(agents.size() + 1);
        assertThat(csv).contains(agents.get(0).agentCode());
        // FA-19: a password must never reach a file.
        assertThat(csv.toLowerCase()).doesNotContain("password");
    }

    private List<FieldAgentDto> search(String term) {
        return fieldAgentService
                .page(new FieldAgentFilter(scope(), null, null, null, term, FieldAgentSort.NAME, false, 0, 25))
                .items();
    }

    private FieldAgentFilter filter(Collection<UUID> areaUnitIds, Boolean paid, int page, int size) {
        return new FieldAgentFilter(areaUnitIds == null ? scope() : areaUnitIds, null, paid, null, null, FieldAgentSort.NAME, false, page, size);
    }

    /**
     * What the controller does on every call: the caller's own scope, narrowed
     * by whatever area they picked. A test that passed null here would be
     * testing an endpoint that does not exist.
     */
    private Collection<UUID> scope() {
        return fieldAgentService.areaUnitIds(null, null, null);
    }

    private long agentCount() {
        return userRepository.findByRoleOrderByNameAsc(Role.FIELD_AGENT).size();
    }

    private User superAdmin() {
        return userRepository.findByRoleOrderByNameAsc(Role.SUPER_ADMIN).stream()
                .filter(User::isActive)
                .findFirst()
                .orElseThrow();
    }

    private User adminScopedTo(Unit unit) {
        User admin = User.builder()
                .id(UUID.randomUUID())
                .name("Scoped admin for " + unit.getName())
                .email("scoped-" + UUID.randomUUID() + "@k-pulse.in")
                .role(Role.ADMIN)
                .scope(AccessScope.builder().level(unit.getLevel()).unit(unit).build())
                .active(true)
                .createdAt(java.time.Instant.now())
                .build();
        return userRepository.save(admin);
    }

    private void signIn(User user) {
        var principal = UserPrincipal.from(user);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
