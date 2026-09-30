package com.kahga.pluse.user.service;

import com.kahga.pluse.common.exception.BusinessException;
import com.kahga.pluse.location.entity.Unit;
import com.kahga.pluse.location.service.LocationService;
import com.kahga.pluse.security.CurrentUserService;
import com.kahga.pluse.user.entity.Role;
import com.kahga.pluse.user.entity.User;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * FR-A9: what the calling admin is allowed to see. Resolved from the account's
 * own scope, never from a request parameter — a client that sends someone else's
 * unitId must still get nothing back.
 *
 * <p>A null scope means unrestricted. Only a SUPER_ADMIN gets one, and it is the
 * role that grants it, not a missing row: an ADMIN whose scope somehow failed to
 * load is refused rather than quietly promoted.
 */
@Service
@RequiredArgsConstructor
public class AdminScopeService {

    private final CurrentUserService currentUserService;
    private final LocationService locationService;

    public Role callerRole() {
        return currentUserService.user().getRole();
    }

    /** Booths the caller may see, or null when unrestricted. */
    public Set<UUID> boothIds() {
        return boothIdsFor(currentUserService.user());
    }

    public Set<UUID> boothIdsFor(User admin) {
        UUID scopeUnitId = scopeUnitId(admin);
        return scopeUnitId == null ? null : locationService.boothIdsUnder(scopeUnitId);
    }

    /** Every unit at or under the caller's scope, or null when unrestricted. */
    public Set<UUID> unitIds() {
        UUID scopeUnitId = scopeUnitId(currentUserService.user());
        return scopeUnitId == null ? null : locationService.idsAtOrUnder(scopeUnitId);
    }

    /** The unit the caller is scoped to, or null when unrestricted. */
    public UUID scopeUnitId() {
        return scopeUnitId(currentUserService.user());
    }

    /** Refuses a unit outside the caller's scope, so a tampered id cannot widen it. */
    public void requireWithinScope(UUID unitId) {
        Set<UUID> allowed = unitIds();
        if (allowed != null && (unitId == null || !allowed.contains(unitId))) {
            throw new BusinessException("That unit is outside your area", HttpStatus.FORBIDDEN);
        }
    }

    /** Narrows a requested unit to the caller's scope: their own scope when they asked for nothing. */
    public UUID resolveRequestedUnit(UUID requestedUnitId) {
        if (requestedUnitId == null) {
            return scopeUnitId();
        }
        requireWithinScope(requestedUnitId);
        return requestedUnitId;
    }

    private UUID scopeUnitId(User admin) {
        if (admin.getRole() == Role.SUPER_ADMIN) {
            return null;
        }
        if (admin.getRole() != Role.ADMIN) {
            throw new BusinessException("You do not have access to that", HttpStatus.FORBIDDEN);
        }
        Unit unit = admin.getScope() == null ? null : admin.getScope().getUnit();
        if (unit == null) {
            // An admin with no scope is a data problem, not a super admin.
            throw new BusinessException(
                    "This admin account has no area assigned — ask a super admin to set one", HttpStatus.FORBIDDEN);
        }
        return unit.getId();
    }
}
