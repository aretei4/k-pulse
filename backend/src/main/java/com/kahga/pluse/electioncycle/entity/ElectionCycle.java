package com.kahga.pluse.electioncycle.entity;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One run-up to an election. Every sentiment entry and house tally is tagged
 * with the cycle it was recorded in, so a new campaign never overwrites what the
 * last one learned (FR-A11).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ElectionCycle {

    private UUID id;

    private String name;

    private ElectionType electionType;

    private int year;

    private CycleStatus status;

    private Instant createdAt;

    private Instant closedAt;
}
