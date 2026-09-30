package com.kahga.pluse.housesentiment.dto;

import com.kahga.pluse.location.entity.UnitLevel;
import java.util.UUID;

/** One row of the admin pre-election dashboard: a unit's house tallies, rolled up. */
public record HouseUnitSummaryDto(
        UUID unitId,
        String unitName,
        UnitLevel unitLevel,
        long houses,
        long people,
        long residents,
        long positive,
        long neutral,
        long negative) {}
