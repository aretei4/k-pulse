package com.kahga.pluse.sentiment.entity;

import com.kahga.pluse.candidate.entity.Candidate;
import com.kahga.pluse.electioncycle.entity.ElectionCycle;
import com.kahga.pluse.user.entity.User;
import com.kahga.pluse.voter.entity.Voter;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row per voter per candidate. Re-recording updates this row rather than
 * appending, which is what "edit any time, no time window" (FR-U10) means here.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentimentEntry {

    private UUID id;

    /** Only the id is populated when read back: entries are read alongside a voter the caller already holds. */
    private Voter voter;

    private Candidate candidate;

    private SentimentValue sentiment;

    private ConfidenceLevel confidence;

    private boolean resident;

    private Integer wardNo;

    /** The cycle this was recorded in; a new cycle starts fresh rows (FR-A11). */
    private ElectionCycle electionCycle;

    private User recordedBy;

    private Instant recordedAt;

    private Instant updatedAt;
}
