package com.kahga.pluse.accessrequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kahga.pluse.accessrequest.entity.AccessRequest;
import com.kahga.pluse.accessrequest.entity.AccessRequestStatus;
import com.kahga.pluse.accessrequest.repository.AccessRequestRepository;
import com.kahga.pluse.accessrequest.service.AccessRequestService;
import com.kahga.pluse.common.exception.BusinessException;
import com.kahga.pluse.location.entity.UnitLevel;
import com.kahga.pluse.location.repository.UnitRepository;
import com.kahga.pluse.location.service.LocationService;
import com.kahga.pluse.user.entity.Role;
import com.kahga.pluse.user.repository.UserRepository;
import java.util.Set;
import java.util.UUID;
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
}
