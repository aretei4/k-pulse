package com.kahga.pluse.user.dto;

import com.kahga.pluse.location.entity.UnitLevel;
import com.kahga.pluse.user.entity.Role;
import com.kahga.pluse.user.entity.User;
import java.util.UUID;

/** Scope fields are null for super admins and field agents — neither carries one. */
public record UserDto(
        UUID id,
        String name,
        String email,
        String phone,
        Role role,
        boolean active,
        /** FR-A20: the readable agent id, A-00272. Null on an admin account. */
        String agentCode,
        /** FR-U13: field agents only — whether PDF download is switched on for them. */
        boolean downloadEnabled,
        /** FA-25: the SPA sends the agent to choose a new password before anything else. */
        boolean mustChangePassword,
        /** FR-U14: whether the agent has the Reports page. The server checks again on every call. */
        boolean reportsEnabled,
        UnitLevel scopeLevel,
        UUID scopeUnitId,
        String scopeUnitName) {

    public static UserDto from(User user) {
        var scope = user.getScope();
        var unit = scope == null ? null : scope.getUnit();
        return new UserDto(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.isActive(),
                user.getAgentNo() == null ? null : "A-%05d".formatted(user.getAgentNo()),
                user.isDownloadEnabled(),
                user.isMustChangePassword(),
                user.isReportsEnabled(),
                scope == null ? null : scope.getLevel(),
                unit == null ? null : unit.getId(),
                unit == null ? null : unit.getName());
    }
}
