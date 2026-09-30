package com.kahga.pluse.user.dto;

import com.kahga.pluse.location.entity.UnitLevel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * A new admin account (FR-A13, super admin only).
 *
 * <p>{@code superAdmin} true makes another unrestricted account and ignores the
 * scope; otherwise the scope is required, because an admin with no area could
 * see nothing at all.
 */
public record CreateAdminRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        @Pattern(regexp = "\\d{10}", message = "must be a 10-digit number") String phone,
        @NotBlank @Size(min = 8, message = "must be at least 8 characters") String password,
        boolean superAdmin,
        UnitLevel scopeLevel,
        UUID scopeUnitId) {}
