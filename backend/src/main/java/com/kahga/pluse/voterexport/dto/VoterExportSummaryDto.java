package com.kahga.pluse.voterexport.dto;

import java.util.List;
import java.util.UUID;

/**
 * What the Download page shows before anything is built: the booth it is about,
 * how many voters each chip holds, and what the current choice would produce.
 *
 * <p>The page count is worked out here rather than in the phone, so the estimate
 * the agent reads and the file they get cannot drift apart.
 */
public record VoterExportSummaryDto(
        UUID boothId,
        String boothName,
        /** The block, for the "Booth 12, Tihidi" line. Null if the booth sits shallower. */
        String blockName,
        String boothPath,
        UUID candidateId,
        String candidateName,
        long totalVoters,
        Counts counts,
        long matching,
        int pages,
        List<PreviewRow> preview,
        String fileName,
        /** DL-10: more matches than this and the file is refused. */
        int maxRows) {

    /** Every chip's own total, whatever is currently chosen. */
    public record Counts(long positive, long neutral, long negative, long notRecorded) {}

    public record PreviewRow(String name, String houseNo, SentimentFilter sentiment) {}
}
