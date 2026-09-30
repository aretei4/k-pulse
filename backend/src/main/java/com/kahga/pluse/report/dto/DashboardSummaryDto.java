package com.kahga.pluse.report.dto;

import java.util.List;

public record DashboardSummaryDto(
        long entriesRecorded,
        long activeAgents,
        long pendingAccessRequests,
        long pendingChangeRequests,
        List<SentimentSummaryDto> byUnit,
        ConfidenceSummaryDto confidence) {}
