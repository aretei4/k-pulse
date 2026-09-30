package com.kahga.pluse.accessrequest.dto;

import com.kahga.pluse.accessrequest.entity.AccessRequest;
import com.kahga.pluse.accessrequest.entity.AccessRequestStatus;
import com.kahga.pluse.location.entity.UnitLevel;
import java.time.Instant;
import java.util.UUID;

public record AccessRequestDto(
        UUID id,
        UUID agentId,
        String agentName,
        UUID unitId,
        UnitLevel unitLevel,
        String unitName,
        String unitPath,
        UUID candidateId,
        String candidateName,
        AccessRequestStatus status,
        Instant requestedAt,
        Instant decidedAt,
        Instant expiresAt,
        String reviewerNote) {

    public static AccessRequestDto from(AccessRequest request) {
        return new AccessRequestDto(
                request.getId(),
                request.getAgent().getId(),
                request.getAgent().getName(),
                request.getUnit().getId(),
                request.getUnit().getLevel(),
                request.getUnit().getName(),
                request.getUnit().getPath(),
                request.getCandidate().getId(),
                request.getCandidate().getName(),
                request.getStatus(),
                request.getRequestedAt(),
                request.getDecidedAt(),
                request.getExpiresAt(),
                request.getReviewerNote());
    }
}
