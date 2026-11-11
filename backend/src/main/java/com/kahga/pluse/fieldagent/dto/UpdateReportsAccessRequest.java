package com.kahga.pluse.fieldagent.dto;

import jakarta.validation.constraints.NotNull;

/** AD-1: gives or removes one agent's Reports page. */
public record UpdateReportsAccessRequest(@NotNull Boolean enabled) {}
