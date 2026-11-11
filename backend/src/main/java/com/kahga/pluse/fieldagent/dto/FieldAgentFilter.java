package com.kahga.pluse.fieldagent.dto;

import java.util.Collection;
import java.util.UUID;

/**
 * What the admin has narrowed the list to (FA-3, FA-5, FA-6, FA-7, FA-8).
 *
 * @param areaUnitIds the units an agent's grant must fall inside, already
 *     narrowed to the caller's own scope by the service. Null means unrestricted;
 *     empty means nothing can match, which is not the same thing
 * @param paid null for the All tab, true or false for the two others
 * @param search one box matching name, mobile or agent id
 * @param sort which column the server sorts on; the browser never sorts (FA-N2)
 */
public record FieldAgentFilter(
        Collection<UUID> areaUnitIds,
        AgentAccessState access,
        Boolean paid,
        /** FA-25: null for "any", true or false to list who has the Reports page. */
        Boolean reportsEnabled,
        String search,
        FieldAgentSort sort,
        boolean descending,
        int page,
        int size) {

    /** Every filter but the payment one: what the three tab counts are taken over. */
    public FieldAgentFilter acrossPaymentStatuses() {
        return new FieldAgentFilter(areaUnitIds, access, null, reportsEnabled, search, sort, descending, page, size);
    }

    public boolean matchesNothing() {
        return areaUnitIds != null && areaUnitIds.isEmpty();
    }
}
