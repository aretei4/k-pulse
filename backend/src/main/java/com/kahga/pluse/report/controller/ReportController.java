package com.kahga.pluse.report.controller;

import com.kahga.pluse.accessrequest.service.AccessRequestService;
import com.kahga.pluse.common.response.ApiResponse;
import com.kahga.pluse.location.entity.UnitLevel;
import com.kahga.pluse.report.dto.DashboardSummaryDto;
import com.kahga.pluse.report.dto.ReportFilterRequest;
import com.kahga.pluse.report.dto.ReportResponse;
import com.kahga.pluse.report.service.ExcelReportGenerator;
import com.kahga.pluse.report.service.PdfReportGenerator;
import com.kahga.pluse.report.service.ReportAggregationService;
import com.kahga.pluse.sentiment.entity.ConfidenceLevel;
import com.kahga.pluse.sentiment.entity.SentimentValue;
import com.kahga.pluse.sentiment.service.SentimentService;
import com.kahga.pluse.user.service.AdminScopeService;
import com.kahga.pluse.user.service.UserService;
import com.kahga.pluse.voterchangerequest.service.VoterChangeRequestService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class ReportController {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ReportAggregationService aggregationService;
    private final AdminScopeService adminScopeService;
    private final ExcelReportGenerator excelReportGenerator;
    private final PdfReportGenerator pdfReportGenerator;
    private final SentimentService sentimentService;
    private final UserService userService;
    private final AccessRequestService accessRequestService;
    private final VoterChangeRequestService voterChangeRequestService;

    @GetMapping("/dashboard")
    public ApiResponse<DashboardSummaryDto> dashboard(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) UUID unitId,
            @RequestParam(required = false) String candidateId,
            @RequestParam(required = false) String sentiment,
            @RequestParam(required = false) String confidence,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        ReportFilterRequest filter = filter(level, unitId, candidateId, sentiment, confidence, from, to);
        ReportResponse report = aggregationService.report(filter, adminScopeService.boothIds());

        return ApiResponse.ok(new DashboardSummaryDto(
                aggregationService.countEntries(filter, adminScopeService.boothIds()),
                userService.countActiveAgents(),
                accessRequestService.countPending(),
                voterChangeRequestService.countPending(),
                report.rows(),
                report.confidence()));
    }

    @GetMapping("/reports/summary")
    public ApiResponse<ReportResponse> summary(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) UUID unitId,
            @RequestParam(required = false) String candidateId,
            @RequestParam(required = false) String sentiment,
            @RequestParam(required = false) String confidence,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        return ApiResponse.ok(aggregationService.report(
                filter(level, unitId, candidateId, sentiment, confidence, from, to), adminScopeService.boothIds()));
    }

    @GetMapping("/reports/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(defaultValue = "excel") String format,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) UUID unitId,
            @RequestParam(required = false) String candidateId,
            @RequestParam(required = false) String sentiment,
            @RequestParam(required = false) String confidence,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        ReportFilterRequest filter = filter(level, unitId, candidateId, sentiment, confidence, from, to);
        ReportResponse report = aggregationService.report(filter, adminScopeService.boothIds());

        boolean pdf = "pdf".equalsIgnoreCase(format);
        byte[] body = pdf ? pdfReportGenerator.generate(report, filter) : excelReportGenerator.generate(report, filter);
        String fileName = "k-pulse-sentiment-%s-%s.%s"
                .formatted(filter.levelOrDefault().name().toLowerCase(), LocalDate.now(), pdf ? "pdf" : "xlsx");

        return ResponseEntity.ok()
                .contentType(pdf ? MediaType.APPLICATION_PDF : MediaType.parseMediaType(XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(body);
    }

    /** The SPA sends the literal "ALL" for an unset filter; treat that as null. */
    private ReportFilterRequest filter(
            String level,
            UUID unitId,
            String candidateId,
            String sentiment,
            String confidence,
            Instant from,
            Instant to) {

        return new ReportFilterRequest(
                blankToNull(level) == null ? UnitLevel.BOOTH : UnitLevel.valueOf(level),
                unitId,
                blankToNull(candidateId) == null ? null : UUID.fromString(candidateId),
                blankToNull(sentiment) == null ? null : SentimentValue.valueOf(sentiment),
                blankToNull(confidence) == null ? null : ConfidenceLevel.valueOf(confidence),
                from,
                to);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() || "ALL".equalsIgnoreCase(value) ? null : value;
    }
}
