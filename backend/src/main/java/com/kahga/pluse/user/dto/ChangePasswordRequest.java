package com.kahga.pluse.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * The agent choosing their own password. {@code currentPassword} may be left
 * out only by an agent who has none yet — the service decides, not the caller.
 */
public record ChangePasswordRequest(String currentPassword, @NotBlank String newPassword) {}
