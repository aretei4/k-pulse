package com.kahga.pluse.housesentiment.dto;

import com.kahga.pluse.sentiment.entity.ConfidenceLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * A house tally. The three sentiment counts have to add up to {@code headcount};
 * the service checks that rather than a bean annotation, so the message can name
 * the shortfall.
 */
public record RecordHouseSentimentRequest(
        @NotNull UUID boothId,
        @NotNull UUID candidateId,
        @NotBlank @Size(max = 40) String houseNo,
        @NotBlank @Size(max = 160) String houseName,
        @Min(1) @Max(200) Integer wardNo,
        @Min(1) @Max(999) int headcount,
        @Min(0) @Max(999) int residentialCount,
        @Min(0) @Max(999) int positiveCount,
        @Min(0) @Max(999) int neutralCount,
        @Min(0) @Max(999) int negativeCount,
        @NotNull ConfidenceLevel confidence) {}
