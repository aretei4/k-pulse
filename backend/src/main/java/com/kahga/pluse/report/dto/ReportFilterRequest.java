package com.kahga.pluse.report.dto;

import com.kahga.pluse.location.entity.UnitLevel;
import com.kahga.pluse.sentiment.entity.ConfidenceLevel;
import com.kahga.pluse.sentiment.entity.SentimentValue;
import java.time.Instant;
import java.util.UUID;

/**
 * Every filter the dashboard and report screens can apply. Null means "no
 * filter"; the SPA's literal "ALL" is normalised to null in the controller.
 */
public record ReportFilterRequest(
        UnitLevel level,
        UUID unitId,
        UUID candidateId,
        SentimentValue sentiment,
        ConfidenceLevel confidence,
        Instant from,
        Instant to,
        /**
         * Leave out the entries recorded against a voter the agent found living
         * elsewhere. Those voters stay on the roll and are still counted in it;
         * what they are left out of is the sentiment split, which is the only
         * part their opinion was ever in.
         */
        boolean residentsOnly) {

    public UnitLevel levelOrDefault() {
        return level == null ? UnitLevel.BOOTH : level;
    }
}
