package com.kahga.pluse.report.controller;

import com.kahga.pluse.accessrequest.service.AccessRequestService;
import com.kahga.pluse.common.response.ApiResponse;
import com.kahga.pluse.location.entity.UnitLevel;
import com.kahga.pluse.report.dto.ReportFilterRequest;
import com.kahga.pluse.report.dto.ReportResponse;
import com.kahga.pluse.report.service.ReportAggregationService;
import com.kahga.pluse.security.CurrentUserService;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Booth-wise charts for the agent — the same aggregation, scoped to their grants. */
@RestController
@RequestMapping("/api/agent/insights")
@RequiredArgsConstructor
public class AgentInsightsController {

    private final ReportAggregationService aggregationService;
    private final AccessRequestService accessRequestService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public ApiResponse<ReportResponse> insights(
            @RequestParam(required = false) UUID boothId, @RequestParam(required = false) UUID candidateId) {

        UUID agentId = currentUserService.user().getId();
        Set<UUID> scope;
        if (boothId != null) {
            accessRequestService.requireGrantFor(agentId, boothId);
            scope = new LinkedHashSet<>(Set.of(boothId));
        } else {
            scope = accessRequestService.accessibleBoothIds(agentId);
        }

        ReportFilterRequest filter =
                new ReportFilterRequest(UnitLevel.BOOTH, null, candidateId, null, null, null, null);
        return ApiResponse.ok(aggregationService.report(filter, scope));
    }
}
