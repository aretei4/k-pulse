package com.kahga.pluse.housesentiment.dto;

import java.util.List;

/** Rows for the chosen level, plus the totals across all of them. */
public record HouseReportResponse(List<HouseUnitSummaryDto> rows, HouseInsightsDto totals) {}
