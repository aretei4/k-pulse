package com.kahga.pluse.sentiment.dto;

import com.kahga.pluse.location.entity.UnitLevel;
import java.time.Instant;
import java.util.UUID;

/**
 * What the candidate screen is asking for. {@code level} and {@code parentUnitId}
 * drive the drill-down: null parent means "start at my scope".
 */
public record CandidateSentimentFilter(
        UnitLevel level, UUID parentUnitId, SentimentSource source, UUID cycleId, Instant from, Instant to) {

    public SentimentSource sourceOrAll() {
        return source == null ? SentimentSource.ALL : source;
    }
}
