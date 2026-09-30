package com.kahga.pluse.housesentiment.dto;

import com.kahga.pluse.report.dto.ConfidenceSummaryDto;

/**
 * Booth-wise totals for the pre-election charts (FR-U12).
 *
 * <p>Kept apart from {@code SentimentSummaryDto}: these are households and the
 * people counted in them, not voters off the roll, so a "not recorded" figure
 * has no meaning here — nothing says how many houses a booth holds.
 */
public record HouseInsightsDto(
        long houses,
        long people,
        long residents,
        long positive,
        long neutral,
        long negative,
        ConfidenceSummaryDto confidence) {

    public static HouseInsightsDto empty() {
        return new HouseInsightsDto(0, 0, 0, 0, 0, 0, new ConfidenceSummaryDto(0, 0, 0));
    }
}
