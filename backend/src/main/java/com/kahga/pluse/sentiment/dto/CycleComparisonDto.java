package com.kahga.pluse.sentiment.dto;

import com.kahga.pluse.electioncycle.entity.CycleStatus;
import java.util.UUID;

/** One cycle's standing for a candidate in the unit being looked at (FR-A11). */
public record CycleComparisonDto(
        UUID cycleId,
        String cycleName,
        int year,
        CycleStatus status,
        CandidateSentimentDto.Split split) {}
