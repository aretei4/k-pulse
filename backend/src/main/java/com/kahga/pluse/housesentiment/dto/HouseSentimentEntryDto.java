package com.kahga.pluse.housesentiment.dto;

import com.kahga.pluse.housesentiment.entity.HouseSentimentEntry;
import com.kahga.pluse.sentiment.entity.ConfidenceLevel;
import java.time.Instant;
import java.util.UUID;

public record HouseSentimentEntryDto(
        UUID id,
        UUID boothId,
        UUID candidateId,
        String houseNo,
        String houseName,
        Integer wardNo,
        int headcount,
        int residentialCount,
        int positiveCount,
        int neutralCount,
        int negativeCount,
        ConfidenceLevel confidence,
        UUID recordedById,
        String recordedByName,
        Instant recordedAt,
        Instant updatedAt) {

    public static HouseSentimentEntryDto from(HouseSentimentEntry entry) {
        return new HouseSentimentEntryDto(
                entry.getId(),
                entry.getBooth().getId(),
                entry.getCandidate().getId(),
                entry.getHouseNo(),
                entry.getHouseName(),
                entry.getWardNo(),
                entry.getHeadcount(),
                entry.getResidentialCount(),
                entry.getPositiveCount(),
                entry.getNeutralCount(),
                entry.getNegativeCount(),
                entry.getConfidence(),
                entry.getRecordedBy().getId(),
                entry.getRecordedBy().getName(),
                entry.getRecordedAt(),
                entry.getUpdatedAt());
    }
}
