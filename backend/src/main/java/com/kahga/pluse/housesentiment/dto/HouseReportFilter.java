package com.kahga.pluse.housesentiment.dto;

import com.kahga.pluse.location.entity.UnitLevel;
import com.kahga.pluse.sentiment.entity.ConfidenceLevel;
import java.time.Instant;
import java.util.UUID;

/**
 * Filters for the pre-election report. The named-voter report also filters by a
 * single sentiment; a house holds all three at once, so there is no equivalent
 * here.
 */
public record HouseReportFilter(
        UnitLevel level, UUID unitId, UUID candidateId, ConfidenceLevel confidence, Instant from, Instant to) {

    public UnitLevel levelOrDefault() {
        return level == null ? UnitLevel.BOOTH : level;
    }
}
