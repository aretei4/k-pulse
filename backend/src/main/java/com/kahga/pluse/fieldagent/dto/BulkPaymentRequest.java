package com.kahga.pluse.fieldagent.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Marking a selection paid or not paid in one action (FA-14, FA-15).
 *
 * @param selection IDS for the rows the admin ticked, ALL_MATCHING for every
 *     agent the current filters match. ALL_MATCHING is resolved on the server at
 *     the moment of the action, never from a list the browser built, so it
 *     cannot reach past the caller's own scope
 * @param amount the amount for <em>each</em> agent, not the total
 */
public record BulkPaymentRequest(
        @NotNull Selection selection,
        List<UUID> ids,
        UUID districtId,
        UUID blockId,
        UUID panchayatId,
        AgentAccessState access,
        Boolean paidFilter,
        String search,
        boolean paid,
        @DecimalMin("0.0") @Digits(integer = 8, fraction = 2) BigDecimal amount,
        LocalDate paidOn,
        PaymentMode mode,
        @Size(max = 120) String reference) {

    public enum Selection {
        IDS,
        ALL_MATCHING
    }
}
