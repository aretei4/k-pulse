package com.kahga.pluse.accountdeletion.dto;

import com.kahga.pluse.accountdeletion.entity.AccountDeletionRequest;
import com.kahga.pluse.accountdeletion.entity.DeletionStatus;
import java.time.Instant;
import java.util.UUID;

public record AccountDeletionRequestDto(
        UUID id,
        UUID userId,
        String agentName,
        String agentPhone,
        String agentEmail,
        String reason,
        DeletionStatus status,
        Instant requestedAt,
        Instant reviewedAt,
        String reviewedByName,
        String reviewerNote,
        Integer deletedEntries) {

    public static AccountDeletionRequestDto from(AccountDeletionRequest request) {
        return new AccountDeletionRequestDto(
                request.getId(),
                request.getUser() == null ? null : request.getUser().getId(),
                request.getAgentName(),
                request.getAgentPhone(),
                request.getAgentEmail(),
                request.getReason(),
                request.getStatus(),
                request.getRequestedAt(),
                request.getReviewedAt(),
                request.getReviewedBy() == null ? null : request.getReviewedBy().getName(),
                request.getReviewerNote(),
                request.getDeletedEntries());
    }
}
