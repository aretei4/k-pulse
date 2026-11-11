package com.kahga.pluse.sentiment.dto;

import com.kahga.pluse.sentiment.entity.ConfidenceLevel;
import com.kahga.pluse.sentiment.entity.SentimentValue;
import com.kahga.pluse.voter.entity.Caste;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RecordSentimentRequest(
        @NotNull UUID candidateId,
        @NotNull SentimentValue sentiment,
        @NotNull ConfidenceLevel confidence,
        boolean resident,
        @Min(1) @Max(200) Integer wardNo,
        /**
         * What the agent found at the door. Null leaves whatever the voter
         * already carries alone, so an agent recording a second candidate is not
         * made to answer it again.
         */
        Caste caste) {}
