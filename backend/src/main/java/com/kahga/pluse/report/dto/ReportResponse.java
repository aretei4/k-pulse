package com.kahga.pluse.report.dto;

import java.util.List;

public record ReportResponse(List<SentimentSummaryDto> rows, ConfidenceSummaryDto confidence) {}
