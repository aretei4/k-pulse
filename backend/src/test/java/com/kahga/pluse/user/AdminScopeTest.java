package com.kahga.pluse.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kahga.pluse.common.exception.BusinessException;
import com.kahga.pluse.housesentiment.dto.HouseReportFilter;
import com.kahga.pluse.housesentiment.service.HouseReportService;
import com.kahga.pluse.location.entity.Unit;
import com.kahga.pluse.location.entity.UnitLevel;
import com.kahga.pluse.location.repository.UnitRepository;
import com.kahga.pluse.location.service.LocationService;
import com.kahga.pluse.security.UserPrincipal;
import com.kahga.pluse.user.dto.CreateAdminRequest;
import com.kahga.pluse.user.entity.AccessScope;
import com.kahga.pluse.user.entity.Role;
import com.kahga.pluse.user.entity.User;
import com.kahga.pluse.user.repository.UserRepository;
import com.kahga.pluse.user.service.AdminScopeService;
import com.kahga.pluse.user.service.UserService;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

/**
 * FR-A9 and FR-A13 — the rules that decide who sees what, and who may create
 * accounts. A bug here is a data-exposure bug, not a cosmetic one, so each is
 * asserted directly rather than through a screen.
 */
@SpringBootTest
@ActiveProfiles("test")
class AdminScopeTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private UserService userService;

    @Autowired
    private AdminScopeService adminScopeService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UnitRepository unitRepository;

    @Autowired
    private LocationService locationService;

    @Autowired
    private HouseReportService houseReportService;

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aSuperAdminIsUnrestricted() {
        signIn(superAdmin());

        assertThat(adminScopeService.boothIds()).isNull();
        assertThat(adminScopeService.unitIds()).isNull();
        assertThat(adminScopeService.scopeUnitId()).isNull();
    }

    @Test
    void aScopedAdminSeesOnlyTheirOwnUnitAndBelow() {
        Unit panchayat = unitRepository.findByLevelOrderByNameAsc(UnitLevel.PANCHAYAT).get(0);
        signIn(scopedAdmin(panchayat));

        Set<UUID> booths = adminScopeService.boothIds();
        Set<UUID> expected = locationService.boothIdsUnder(panchayat.getId());

        assertThat(booths).isNotNull().isEqualTo(expected);
        assertThat(adminScopeService.unitIds()).contains(panchayat.getId());

        // A booth in another panchayat is outside the scope, whoever asks for it.
        Unit elsewhere = unitRepository.findByLevelOrderByNameAsc(UnitLevel.BOOTH).stream()
                .filter(b -> !expected.contains(b.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("seed data has only one panchayat's worth of booths"));
        assertThatThrownBy(() -> adminScopeService.requireWithinScope(elsewhere.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("outside your area");
    }

    @Test
    void aTamperedUnitIdCannotWidenTheHouseReport() {
        Unit panchayat = unitRepository.findByLevelOrderByNameAsc(UnitLevel.PANCHAYAT).get(0);
        Unit district = unitRepository.findByLevelOrderByNameAsc(UnitLevel.DISTRICT).get(0);
        signIn(scopedAdmin(panchayat));

        // Asking for the whole district as a panchayat admin: the answer stays
        // inside the panchayat rather than widening to the district.
        var asked = houseReportService.report(
                new HouseReportFilter(UnitLevel.BOOTH, district.getId(), null, null, null, null));
        Set<UUID> mine = locationService.boothIdsUnder(panchayat.getId());

        assertThat(asked.rows()).allSatisfy(row -> assertThat(mine).contains(row.unitId()));
    }

    @Test
    void onlyASuperAdminCanCreateAnAdmin() {
        Unit panchayat = unitRepository.findByLevelOrderByNameAsc(UnitLevel.PANCHAYAT).get(0);
        signIn(scopedAdmin(panchayat));

        assertThatThrownBy(() -> userService.createAdmin(adminRequest(false, UnitLevel.PANCHAYAT, panchayat.getId())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only a super admin");
    }

    @Test
    void aSuperAdminCreatesScopedAdminsAndOtherSuperAdmins() {
        signIn(superAdmin());
        Unit block = unitRepository.findByLevelOrderByNameAsc(UnitLevel.BLOCK).get(0);

        User scoped = userService.createAdmin(adminRequest(false, UnitLevel.BLOCK, block.getId()));
        User another = userService.createAdmin(adminRequest(true, null, null));

        assertThat(scoped.getRole()).isEqualTo(Role.ADMIN);
        assertThat(scoped.getScope().getUnit().getId()).isEqualTo(block.getId());
        assertThat(another.getRole()).isEqualTo(Role.SUPER_ADMIN);
        assertThat(another.getScope()).isNull();
        // Read back, so the scope survives the round trip through the database.
        assertThat(userRepository.findById(scoped.getId()).orElseThrow().getScope().getLevel())
                .isEqualTo(UnitLevel.BLOCK);
    }

    @Test
    void anAdminCannotBeScopedToABooth() {
        signIn(superAdmin());
        Unit booth = unitRepository.findByLevelOrderByNameAsc(UnitLevel.BOOTH).get(0);

        assertThatThrownBy(() -> userService.createAdmin(adminRequest(false, UnitLevel.BOOTH, booth.getId())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not a booth");
    }

    @Test
    void theLastSuperAdminCannotBeDeactivated() {
        signIn(superAdmin());
        User onlyOneLeft = userRepository.findByRoleOrderByNameAsc(Role.SUPER_ADMIN).stream()
                .filter(User::isActive)
                .findFirst()
                .orElseThrow();

        // Switch off every other super admin first, so this one really is the last.
        userRepository.findByRoleOrderByNameAsc(Role.SUPER_ADMIN).stream()
                .filter(u -> u.isActive() && !u.getId().equals(onlyOneLeft.getId()))
                .forEach(u -> userService.setActive(u.getId(), false));

        assertThatThrownBy(() -> userService.setActive(onlyOneLeft.getId(), false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("last active super admin");
        assertThat(userRepository.findById(onlyOneLeft.getId()).orElseThrow().isActive())
                .isTrue();
    }

    private CreateAdminRequest adminRequest(boolean superAdmin, UnitLevel level, UUID unitId) {
        int seq = SEQUENCE.incrementAndGet();
        return new CreateAdminRequest(
                "Scope test " + seq,
                "scope-test-" + seq + "@k-pulse.test",
                null,
                "Password" + seq + "!",
                superAdmin,
                level,
                unitId);
    }

    private User superAdmin() {
        return userRepository.findByRoleOrderByNameAsc(Role.SUPER_ADMIN).stream()
                .filter(User::isActive)
                .findFirst()
                .orElseThrow(() -> new AssertionError("the bootstrap super admin is missing"));
    }

    private User scopedAdmin(Unit unit) {
        int seq = SEQUENCE.incrementAndGet();
        return userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .name("Scoped admin " + seq)
                .email("scoped-" + seq + "@k-pulse.test")
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
