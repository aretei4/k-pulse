package com.kahga.pluse.user.dto;

import java.time.Instant;

/**
 * What the agent's own Sign-in card shows (FA-24). Never the password, and
 * never its hash — only whether one exists.
 */
public record AgentPasswordStatusDto(
        boolean passwordSet, boolean mustChangePassword, Instant resetRequestedAt) {}
