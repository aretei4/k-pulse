package com.kahga.pluse.sentiment.dto;

import com.kahga.pluse.sentiment.entity.ConfidenceLevel;
import com.kahga.pluse.sentiment.entity.SentimentEntry;
import com.kahga.pluse.sentiment.entity.SentimentValue;
import java.time.Instant;
import java.util.UUID;

public record SentimentEntryDto(
        UUID id,
        UUID voterId,
        UUID candidateId,
        SentimentValue sentiment,
        ConfidenceLevel confidence,
        boolean resident,
        Integer wardNo,
        UUID recordedById,
        String recordedByName,
        Instant recordedAt,
        Instant updatedAt) {

    public static SentimentEntryDto from(SentimentEntry entry) {
        return new SentimentEntryDto(
                entry.getId(),
                entry.getVoter().getId(),
                entry.getCandidate().getId(),
                entry.getSentiment(),
                entry.getConfidence(),
                entry.isResident(),
                entry.getWardNo(),
                entry.getRecordedBy().getId(),
                entry.getRecordedBy().getName(),
                entry.getRecordedAt(),
                entry.getUpdatedAt());
    }
}
