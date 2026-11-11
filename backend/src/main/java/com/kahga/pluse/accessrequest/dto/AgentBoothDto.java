package com.kahga.pluse.accessrequest.dto;

import com.kahga.pluse.accessrequest.entity.AccessRequest;
import com.kahga.pluse.location.entity.Unit;
import java.time.Instant;
import java.util.UUID;

/** A booth the agent can open, carrying the candidate its grant is tied to. */
public record AgentBoothDto(
        UUID boothId,
        String boothName,
        String path,
        String districtName,
        String blockName,
        String panchayatName,
        UUID candidateId,
        String candidateName,
        UUID accessRequestId,
        Instant expiresAt) {

    private static final String SEPARATOR = " > ";

    /**
     * The district, block and panchayat are read off the booth's own path rather
     * than by walking up the table. LocationService builds that path root-first
     * and rebuilds it whenever a unit is renamed, so it is as true as the rows
     * above it, and reading it keeps this to the one lookup per booth it already
     * costs rather than four.
     */
    public static AgentBoothDto of(Unit booth, AccessRequest grant) {
        String[] parts = booth.getPath() == null ? new String[0] : booth.getPath().split(SEPARATOR);
        return new AgentBoothDto(
                booth.getId(),
                booth.getName(),
                booth.getPath(),
                segment(parts, 0),
                segment(parts, 1),
                segment(parts, 2),
                grant.getCandidate().getId(),
                grant.getCandidate().getName(),
                grant.getId(),
                grant.getExpiresAt());
    }

    /** Null rather than a guess when a booth sits shallower than the usual four levels. */
    private static String segment(String[] parts, int index) {
        return index < parts.length ? parts[index] : null;
    }
}
