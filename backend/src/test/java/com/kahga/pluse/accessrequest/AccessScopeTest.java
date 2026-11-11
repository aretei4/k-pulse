package com.kahga.pluse.accessrequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kahga.pluse.accessrequest.dto.CreateAccessRequestDto;
import com.kahga.pluse.accessrequest.entity.AccessRequest;
import com.kahga.pluse.accessrequest.entity.AccessRequestStatus;
import com.kahga.pluse.accessrequest.repository.AccessRequestRepository;
import com.kahga.pluse.accessrequest.service.AccessRequestService;
import com.kahga.pluse.candidate.repository.CandidateRepository;
import com.kahga.pluse.common.exception.BusinessException;
import com.kahga.pluse.location.entity.Unit;
import com.kahga.pluse.location.entity.UnitLevel;
import com.kahga.pluse.location.repository.UnitRepository;
import com.kahga.pluse.location.service.LocationService;
import com.kahga.pluse.user.entity.Role;
import com.kahga.pluse.user.entity.User;
import com.kahga.pluse.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Exercises the rule the whole access model rests on: a grant at one level
 * unlocks every booth beneath it, and nothing outside it.
 */
@SpringBootTest
@ActiveProfiles("test")
class AccessScopeTest {

    @Autowired
    private AccessRequestService accessRequestService;

    @Autowired
    private LocationService locationService;

    @Autowired
    private AccessRequestRepository accessRequestRepository;

    @Autowired
    private UnitRepository unitRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CandidateRepository candidateRepository;

    @Test
    void panchayatGrantCoversEveryBoothBeneathIt() {
        var panchayat = unitRepository.findByLevelOrderByNameAsc(UnitLevel.PANCHAYAT).stream()
                .filter(unit -> !locationService.boothIdsUnder(unit.getId()).isEmpty())
                .findFirst()
                .orElseThrow();

        Set<UUID> booths = locationService.boothIdsUnder(panchayat.getId());
        assertThat(booths).isNotEmpty();

        booths.forEach(boothId ->
                assertThat(locationService.require(boothId).getLevel()).isEqualTo(UnitLevel.BOOTH));
    }

    @Test
    void agentSeesOnlyTheBoothsTheirLiveGrantsCover() {
        var agent = userRepository.findByRoleOrderByNameAsc(Role.FIELD_AGENT).stream()
                .filter(user -> !accessRequestService.liveGrants(user.getId()).isEmpty())
                .findFirst()
                .orElseThrow();

        Set<UUID> accessible = accessRequestService.accessibleBoothIds(agent.getId());
        assertThat(accessible).isNotEmpty();

        UUID unreachable = unitRepository.findByLevelOrderByNameAsc(UnitLevel.BOOTH).stream()
                .map(unit -> unit.getId())
                .filter(id -> !accessible.contains(id))
                .findFirst()
                .orElseThrow();

        assertThatThrownBy(() -> accessRequestService.requireGrantFor(agent.getId(), unreachable))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("do not have access");
    }

    @Test
    void onlyAPanchayatCanBeAskedFor() {
        User agent = newAgent();
        for (UnitLevel level : List.of(UnitLevel.DISTRICT, UnitLevel.BLOCK, UnitLevel.BOOTH)) {
            UUID unitId = unitRepository
                    .findByLevelOrderByNameAsc(level)
                    .get(0)
                    .getId();
            assertThatThrownBy(() ->
                            accessRequestService.create(agent, new CreateAccessRequestDto(unitId, "Sabita Pradhan")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("one panchayat at a time");
        }
        assertThat(accessRequestService.listForAgent(agent.getId())).isEmpty();
    }

    @Test
    void aCandidateNamedForTheFirstTimeIsAdded() {
        String name = "Niranjan Test " + UUID.randomUUID().toString().substring(0, 8);
        assertThat(candidateRepository.findByNameIgnoreCase(name)).isEmpty();

        var request = accessRequestService.create(
                newAgent(), new CreateAccessRequestDto(panchayatWithBooths().getId(), name));

        assertThat(request.getCandidate().getName()).isEqualTo(name);
        assertThat(candidateRepository.findByNameIgnoreCase(name)).isPresent();
        assertThat(request.getStatus()).isEqualTo(AccessRequestStatus.PENDING);
    }

    /** Otherwise one candidate's sentiment would split across two rows. */
    @Test
    void theSameNameTypedDifferentlyIsTheSameCandidate() {
        String name = "Pramila Test " + UUID.randomUUID().toString().substring(0, 8);
        List<Unit> panchayats = unitRepository.findByLevelOrderByNameAsc(UnitLevel.PANCHAYAT);

        var first = accessRequestService.create(
                newAgent(), new CreateAccessRequestDto(panchayats.get(0).getId(), name));
        var second = accessRequestService.create(
                newAgent(),
                new CreateAccessRequestDto(panchayats.get(1).getId(), "  " + name.toUpperCase() + "  "));

        assertThat(second.getCandidate().getId()).isEqualTo(first.getCandidate().getId());
        assertThat(second.getCandidate().getName()).isEqualTo(name);
    }

    @Test
    void revokingAGrantEndsAccessImmediately() {
        var grant = accessRequestRepository.findAll().stream()
                .filter(AccessRequest::isLive)
                .findFirst()
                .orElseThrow();
        UUID agentId = grant.getAgent().getId();
        UUID boothId = locationService.boothIdsUnder(grant.getUnit().getId()).iterator().next();

        assertThat(accessRequestService.accessibleBoothIds(agentId)).contains(boothId);

        accessRequestService.revoke(grant.getId());

        assertThat(accessRequestRepository.findById(grant.getId()).orElseThrow().getStatus())
                .isEqualTo(AccessRequestStatus.REVOKED);
        assertThat(accessRequestService.accessibleBoothIds(agentId)).doesNotContain(boothId);
    }

    private Unit panchayatWithBooths() {
        return unitRepository.findByLevelOrderByNameAsc(UnitLevel.PANCHAYAT).stream()
                .filter(unit -> !locationService.boothIdsUnder(unit.getId()).isEmpty())
                .findFirst()
                .orElseThrow();
    }

    /**
     * A throwaway agent per request, so asking twice never trips the one-pending
     * -request-per-pairing rule and no seeded agent's grants are disturbed.
     */
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private User newAgent() {
        int seq = SEQUENCE.incrementAndGet();
        String suffix = String.format("%09d", seq);
        return userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .name("Access scope test " + seq)
                .phone("6" + suffix)
                .email("access-scope-" + suffix + "@k-pulse.test")
                .role(Role.FIELD_AGENT)
                .active(true)
                .createdAt(Instant.now())
                .build());
    }
}
