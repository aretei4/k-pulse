package com.kahga.pluse.fieldagent.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One agent, as both the table row and the detail panel need them (FA-1, §5).
 *
 * <p>One shape rather than two: the panel's extra fields come from the same row
 * of the same query, and a second trip to the server to open a panel over a row
 * the browser is already holding would be a trip for nothing.
 *
 * @param agentCode the readable id, A-00272
 * @param candidateName the candidate of the grant the Area and Access describe
 * @param votersRecorded sentiment entries this agent has recorded, all cycles
 * @param lastActive when they last recorded one, null if they never have
 */
public record FieldAgentDto(
        UUID id,
        String agentCode,
        String name,
        String phone,
        String email,
        String address,
        /** What the agent gave at sign-up when asked who sent them; often null. */
        String referralNo,
        boolean active,
        UUID areaUnitId,
        String panchayatName,
        String blockName,
        String districtName,
        String candidateName,
        AgentAccessState access,
        Instant accessExpiresAt,
        long votersRecorded,
        Instant lastActive,
        Instant joined,
        boolean downloadEnabled,
        /** FA-25: whether a password exists at all, never the password itself. */
        /** FR-A22: whether the agent has the Reports page in the app. */
        boolean reportsEnabled,
        boolean passwordSet,
        boolean mustChangePassword,
        /** FA-24: when the agent asked for a reset; null when nobody is waiting. */
        Instant passwordResetRequestedAt,
        boolean paid,
        BigDecimal paidAmount,
        LocalDate paidOn,
        PaymentMode paidMode,
        String paidRef,
        Instant paidUpdatedAt,
        String paidUpdatedByName) {

    /** "Narendrapur, Tihidi, Bhadrak", as the panel's Address line reads it back. */
    public String area() {
        StringBuilder area = new StringBuilder();
        for (String part : new String[] {panchayatName, blockName, districtName}) {
            if (part != null && !part.isBlank()) {
                area.append(area.isEmpty() ? "" : ", ").append(part);
            }
        }
        return area.toString();
    }
}
