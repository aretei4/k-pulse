package com.kahga.pluse.voterexport.dto;

import com.kahga.pluse.voter.entity.Gender;
import java.time.Instant;

/**
 * One line of the walk list. Deliberately narrow: it holds what DL-6 prints and
 * nothing else, so house-level sentiment (DL-11) cannot reach the file by
 * someone adding a field to a shared DTO.
 */
public record ExportVoterRow(
        /**
         * Only the agent's report export fills this in, where the list can span
         * several booths; the booth walk list (DL-6) leaves it null and prints
         * no such column.
         */
        String boothName,
        String name,
        String relation,
        String houseNo,
        int age,
        Gender gender,
        /** Null when nothing has been recorded for this voter and candidate. */
        SentimentFilter sentiment,
        Instant recordedAt) {

    /** "Not recorded" reads as a sentiment on the page, so it is one here too. */
    public SentimentFilter sentimentOrNotRecorded() {
        return sentiment == null ? SentimentFilter.NOT_RECORDED : sentiment;
    }
}
