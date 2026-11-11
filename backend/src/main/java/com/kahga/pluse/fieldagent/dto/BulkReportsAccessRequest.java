package com.kahga.pluse.fieldagent.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * Giving or removing the Reports page for a selection (FA-26, AD-4).
 *
 * <p>The same shape as the bulk payment action, and for the same reason:
 * ALL_MATCHING is resolved on the server from the filters, never from a list
 * the browser assembled, so it cannot reach an agent the admin may not see.
 */
public record BulkReportsAccessRequest(
        @NotNull BulkPaymentRequest.Selection selection,
        List<UUID> ids,
        UUID districtId,
        UUID blockId,
        UUID panchayatId,
        AgentAccessState access,
        Boolean paidFilter,
        Boolean reportsFilter,
        String search,
        boolean enabled) {}
