package com.kahga.pluse.fieldagent.dto;

import java.util.List;

/**
 * One page of the list, with the three tab counts beside it (FA-2, FA-7).
 *
 * <p>The counts are taken over every filter except the payment one, so Paid and
 * Not paid always add up to All for what the admin is looking at.
 */
public record FieldAgentPageDto(
        List<FieldAgentDto> items,
        int page,
        int size,
        long total,
        long allCount,
        long paidCount,
        long unpaidCount) {

    public int totalPages() {
        return size <= 0 ? 0 : (int) ((total + size - 1) / size);
    }
}
