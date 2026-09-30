package com.kahga.pluse.report.dto;

import com.kahga.pluse.location.entity.UnitLevel;
import java.util.UUID;

public record SentimentSummaryDto(
        UUID unitId,
        String unitName,
        UnitLevel unitLevel,
        long positive,
        long neutral,
        long negative,
        long notRecorded,
        long total) {}
