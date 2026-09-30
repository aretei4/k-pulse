package com.kahga.pluse.user.service;

import com.kahga.pluse.accessrequest.repository.AccessRequestRepository;
import com.kahga.pluse.common.exception.BusinessException;
import com.kahga.pluse.common.exception.NotFoundException;
import com.kahga.pluse.location.entity.Unit;
import com.kahga.pluse.location.entity.UnitLevel;
import com.kahga.pluse.location.service.LocationService;
import com.kahga.pluse.user.dto.CreateAdminRequest;
import com.kahga.pluse.user.dto.CreateUserRequest;
import com.kahga.pluse.user.dto.UpdateAdminScopeRequest;
import com.kahga.pluse.user.entity.AccessScope;
import com.kahga.pluse.user.entity.Role;
import com.kahga.pluse.user.entity.User;
import com.kahga.pluse.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accounts. Two rules apply here, deliberately kept apart rather than folded
 * into one rule with a role parameter:
 *
 * <ul>
 *   <li>Field agents are managed by any admin, but a scoped admin only sees the
 *       ones working in their area (FR-A7). An agent account carries no unit of
 *       its own, so "their area" means the agent has asked for access somewhere
 *       inside it.
 *   <li>Admin and super-admin accounts are managed by a SUPER_ADMIN only
 *       (FR-A13), with no scope filter — "an admin narrower than mine" is not a
 *       permission this version expresses.
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final AdminScopeService adminScopeService;
    private final AccessRequestRepository accessRequestRepository;
    private final LocationService locationService;
    private final PasswordEncoder passwordEncoder;

    public List<User> list(Role role) {
        List<User> all = role == null
                ? userRepository.findAllByOrderByNameAsc()
                : userRepository.findByRoleOrderByNameAsc(role);

        Set<UUID> agentsInScope = agentIdsInCallerScope();
        return all.stream()
                .filter(user -> user.getRole().isAdminKind() || agentsInScope == null || agentsInScope.contains(user.getId()))
                .toList();
    }

    public long countActiveAgents() {
        return userRepository.countByRoleAndActiveTrue(Role.FIELD_AGENT);
    }

    @Transactional
    public User createFieldAgent(CreateUserRequest request) {
        requireEmailAndPhoneFree(request.email(), request.phone());
        return userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .name(request.name().trim())
                .email(request.email().trim().toLowerCase())
                .phone(request.phone())
                .role(Role.FIELD_AGENT)
                .active(true)
                .createdAt(Instant.now())
                .build());
    }

    /** FR-A13: super admins only — checked here as well as in SecurityConfig. */
    @Transactional
    public User createAdmin(CreateAdminRequest request) {
        requireSuperAdmin();
        requireEmailAndPhoneFree(request.email(), request.phone());

        AccessScope scope = request.superAdmin() ? null : resolveScope(request.scopeLevel(), request.scopeUnitId());
        return userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .name(request.name().trim())
                .email(request.email().trim().toLowerCase())
                .phone(request.phone() == null || request.phone().isBlank() ? null : request.phone())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.superAdmin() ? Role.SUPER_ADMIN : Role.ADMIN)
                .scope(scope)
                .active(true)
                .createdAt(Instant.now())
                .build());
    }

    @Transactional
    public User updateAdminScope(UUID id, UpdateAdminScopeRequest request) {
        requireSuperAdmin();
        User admin = require(id);
        if (!admin.getRole().isAdminKind()) {
            throw new BusinessException("That account is a field agent, not an admin");
        }
        if (admin.getRole() == Role.SUPER_ADMIN) {
            throw new BusinessException("A super admin sees everything, so there is no area to set");
        }
        admin.setScope(resolveScope(request.scopeLevel(), request.scopeUnitId()));
        return userRepository.save(admin);
    }

    @Transactional
    public User setActive(UUID id, boolean active) {
        User user = require(id);
        if (user.getRole().isAdminKind()) {
            requireSuperAdmin();
            if (!active) {
                refuseIfLastSuperAdmin(user);
            }
        } else {
            Set<UUID> agentsInScope = agentIdsInCallerScope();
            if (agentsInScope != null && !agentsInScope.contains(user.getId())) {
                throw new BusinessException("That agent works outside your area", HttpStatus.FORBIDDEN);
            }
        }
        user.setActive(active);
        return userRepository.save(user);
    }

    public User require(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
    }

    /**
     * FR-A13: someone must always be able to administer K-Pulse, so the last
     * active super admin cannot be switched off.
     */
    private void refuseIfLastSuperAdmin(User user) {
        if (user.getRole() != Role.SUPER_ADMIN || !user.isActive()) {
            return;
        }
        if (userRepository.countByRoleAndActiveTrue(Role.SUPER_ADMIN) <= 1) {
            throw new BusinessException(
                    "This is the last active super admin — create another one first, or no one could administer K-Pulse",
                    HttpStatus.CONFLICT);
        }
    }

    /** Agent ids the caller may manage, or null when the caller is unrestricted. */
    private Set<UUID> agentIdsInCallerScope() {
        Set<UUID> unitIds = adminScopeService.unitIds();
        return unitIds == null ? null : accessRequestRepository.agentIdsForUnits(unitIds);
    }

    private AccessScope resolveScope(UnitLevel level, UUID unitId) {
        if (level == null || unitId == null) {
            throw new BusinessException("Pick the district, block or panchayat this admin looks after");
        }
        if (!AccessScope.ALLOWED.contains(level)) {
            throw new BusinessException("An admin is scoped to a district, block or panchayat — not a booth");
        }
        Unit unit = locationService.require(unitId);
        if (unit.getLevel() != level) {
            throw new BusinessException(unit.getName() + " is a " + unit.getLevel().name().toLowerCase() + ", not a "
                    + level.name().toLowerCase());
        }
        return AccessScope.builder().level(level).unit(unit).build();
    }

    private void requireSuperAdmin() {
        if (adminScopeService.callerRole() != Role.SUPER_ADMIN) {
            throw new BusinessException("Only a super admin can manage admin accounts", HttpStatus.FORBIDDEN);
        }
    }

    private void requireEmailAndPhoneFree(String email, String phone) {
        if (phone != null && !phone.isBlank() && userRepository.existsByPhone(phone)) {
            throw new BusinessException("That phone number is already registered", HttpStatus.CONFLICT);
        }
        if (email != null && !email.isBlank() && userRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException("That email is already registered", HttpStatus.CONFLICT);
        }
    }
}
