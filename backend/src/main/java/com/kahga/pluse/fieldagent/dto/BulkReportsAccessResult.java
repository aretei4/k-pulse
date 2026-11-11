package com.kahga.pluse.fieldagent.dto;

/** What a bulk Reports-access action did, for the message afterwards (AD-4). */
public record BulkReportsAccessResult(int agents, boolean enabled) {}
