package com.kahga.pluse.agentreport.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One panchayat the agent may report on, and the candidate it was approved for
 * (AR-3, AR-4).
 *
 * <p>Sent as a list, one entry per live grant, so the page can lock District ›
 * Block › Panchayat and offer a choice only where the agent genuinely has more
 * than one. There is no endpoint that takes a block or a district: an agent
 * cannot ask for a report wider than this.
 *
 * @param booths the approved booths, which are the whole of the report
 * @param wards the ward numbers that occur in those booths, for AG-3
 */
public record AgentReportScopeDto(
        UUID panchayatId,
        String panchayatName,
        String blockName,
        String districtName,
        UUID candidateId,
        String candidateName,
        Instant expiresAt,
        List<BoothRef> booths,
        List<Integer> wards) {

    public record BoothRef(UUID id, String name) {}
}
