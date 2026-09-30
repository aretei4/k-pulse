package com.kahga.pluse.sentiment.dto;

import com.kahga.pluse.sentiment.entity.ConfidenceLevel;
import com.kahga.pluse.sentiment.entity.SentimentValue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RecordSentimentRequest(
        @NotNull UUID candidateId,
        @NotNull SentimentValue sentiment,
        @NotNull ConfidenceLevel confidence,
        boolean resident,
        @Min(1) @Max(200) Integer wardNo) {}
