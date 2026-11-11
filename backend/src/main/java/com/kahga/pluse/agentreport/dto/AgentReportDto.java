package com.kahga.pluse.agentreport.dto;

import java.util.List;
import java.util.UUID;

/**
 * The drawn report: one row per approved booth, plus the same figures for the
 * agent's booths together (AG-5).
 *
 * <p>"Not recorded" is the roll minus every voter who has an entry for this
 * candidate at all, never minus only the entries the confidence or date choice
 * admits. A voter visited last month is not waiting to be visited because the
 * agent is looking at the last seven days.
 */
public record AgentReportDto(List<BoothRow> booths, BoothRow totals, int boothCount) {

    /**
     * @param voters the roll of that booth, narrowed by the ward choice
     * @param notRecorded voters on that roll with no entry for this candidate
     */
    public record BoothRow(
            UUID boothId,
            String boothName,
            long positive,
            long neutral,
            long negative,
            long notRecorded,
            long voters) {

        public long recorded() {
            return positive + neutral + negative;
        }
    }
}
