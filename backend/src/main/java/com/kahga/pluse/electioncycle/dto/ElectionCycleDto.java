package com.kahga.pluse.electioncycle.dto;

import com.kahga.pluse.electioncycle.entity.CycleStatus;
import com.kahga.pluse.electioncycle.entity.ElectionCycle;
import com.kahga.pluse.electioncycle.entity.ElectionType;
import java.time.Instant;
import java.util.UUID;

public record ElectionCycleDto(
        UUID id,
        String name,
        ElectionType electionType,
        int year,
        CycleStatus status,
        Instant createdAt,
        Instant closedAt) {

    public static ElectionCycleDto from(ElectionCycle cycle) {
        return new ElectionCycleDto(
                cycle.getId(),
                cycle.getName(),
                cycle.getElectionType(),
                cycle.getYear(),
                cycle.getStatus(),
                cycle.getCreatedAt(),
                cycle.getClosedAt());
    }
}
