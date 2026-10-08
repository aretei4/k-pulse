package com.kahga.pluse.sentiment.dto;

import com.kahga.pluse.location.entity.UnitLevel;
import java.util.List;
import java.util.UUID;

/**
 * FR-A15: one candidate's standing, and the units driving it.
 *
 * <p>The verdict and every net lead are computed on the server so the rule lives
 * in one place — a screen that re-derived "positive" from the same numbers would
 * eventually disagree with an export that did the same.
 */
public record CandidateSentimentDto(
        UUID candidateId,
        String candidateName,
        /** The panchayat this candidate contests, when one is mapped (null = whole constituency). */
        UUID candidateUnitId,
        String candidateUnitName,
        UnitLevel level,
        UUID parentUnitId,
        String parentUnitPath,
        Split totals,
        List<Row> rows) {

    /**
     * The whole split as the screen needs it.
     *
     * <p>Every derived figure is a record component, not a helper method: a
     * record only serialises its components, so a computed `total()` method
     * would simply be missing from the JSON — which is exactly the bug this
     * shape prevents. Build one with {@link #of}, never the canonical
     * constructor, so the arithmetic has one home.
     */
    public record Split(
            long positive,
            long neutral,
            long negative,
            long total,
            double positivePercent,
            double neutralPercent,
            double negativePercent,
            double netLead,
            Verdict verdict) {

        public static Split of(long positive, long neutral, long negative) {
            long total = positive + neutral + negative;
            double positivePercent = percent(positive, total);
            double negativePercent = percent(negative, total);
            double netLead = positivePercent - negativePercent;
            // "No data" is not a thumbs-down: a unit nobody has canvassed yet
            // must not read as a candidate doing badly there.
            Verdict verdict = total == 0 ? Verdict.NO_DATA : netLead > 0 ? Verdict.POSITIVE : Verdict.NEGATIVE;
            return new Split(
                    positive,
                    neutral,
                    negative,
                    total,
                    positivePercent,
                    percent(neutral, total),
                    negativePercent,
                    netLead,
                    verdict);
        }

        /** Safe 20+ points, Watch 5 to under 20, At risk under 5 (FR-A15). */
        public Status status(double safeFrom, double watchFrom) {
            if (total == 0) {
                return Status.NO_DATA;
            }
            if (netLead >= safeFrom) {
                return Status.SAFE;
            }
            return netLead >= watchFrom ? Status.WATCH : Status.AT_RISK;
        }

        private static double percent(long part, long total) {
            return total == 0 ? 0 : (part * 100.0) / total;
        }
    }

    public enum Verdict {
        POSITIVE,
        NEGATIVE,
        NO_DATA
    }

    public enum Status {
        SAFE,
        WATCH,
        AT_RISK,
        NO_DATA
    }

    /** One unit at the level being shown; {@code drillable} is false at booth level. */
    public record Row(
            UUID unitId,
            String unitName,
            UnitLevel unitLevel,
            Split split,
            double netLead,
            Status status,
            boolean drillable) {}
}
