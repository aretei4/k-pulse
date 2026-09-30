package com.kahga.pluse.housesentiment.dto;

import com.kahga.pluse.sentiment.entity.ConfidenceLevel;
import java.time.Instant;
import java.util.UUID;

/**
 * One recorded house, as the admin's unit drill-down lists it. Unlike the
 * agent's own view this carries the booth and the agent's name, because a unit
 * above booth level gathers houses from several booths and several agents.
 */
public record HouseEntryRowDto(
        UUID id,
        String houseNo,
        String houseName,
        Integer wardNo,
        int headcount,
        int residentialCount,
        int positiveCount,
        int neutralCount,
        int negativeCount,
        ConfidenceLevel confidence,
        UUID boothId,
        String boothName,
        String boothPath,
        String candidateName,
        String recordedByName,
        Instant recordedAt,
        Instant updatedAt) {}
