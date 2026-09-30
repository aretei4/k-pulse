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
                scope == null ? null : scope.getLevel(),
                unit == null ? null : unit.getId(),
                unit == null ? null : unit.getName());
    }
}
