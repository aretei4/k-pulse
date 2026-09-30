package com.kahga.pluse.accessrequest.dto;

import java.time.Instant;
import java.util.UUID;

/** A booth the agent can open, carrying the candidate its grant is tied to. */
public record AgentBoothDto(
        UUID boothId,
        String boothName,
        String path,
        UUID candidateId,
        String candidateName,
        UUID accessRequestId,
        Instant expiresAt) {}
