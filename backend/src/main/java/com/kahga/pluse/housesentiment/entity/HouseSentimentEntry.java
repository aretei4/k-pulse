package com.kahga.pluse.housesentiment.entity;

import com.kahga.pluse.candidate.entity.Candidate;
import com.kahga.pluse.location.entity.Unit;
import com.kahga.pluse.sentiment.entity.ConfidenceLevel;
import com.kahga.pluse.user.entity.User;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row per house per candidate within a booth — the agent's headcount tally
 * before or instead of named-voter canvassing (FR-U12).
 *
 * <p>There is deliberately no voter here, and no per-person name, age, gender or
 * EPIC no.: the only identity carried is the one house name for the household.
 * Confidence reuses the named-voter scale, which is all the two records share.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HouseSentimentEntry {

    private UUID id;

    private Unit booth;

    private Candidate candidate;

    private String houseNo;

    private String houseName;

    private Integer wardNo;

    /** People at the house, of whom {@code residentialCount} actually live there. */
    private int headcount;

    private int residentialCount;

    private int positiveCount;

    private int neutralCount;

    private int negativeCount;

    private ConfidenceLevel confidence;

    private User recordedBy;

    private Instant recordedAt;

    private Instant updatedAt;
}
