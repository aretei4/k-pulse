package com.kahga.pluse.housesentiment.controller;

import com.kahga.pluse.common.response.ApiResponse;
import com.kahga.pluse.housesentiment.dto.HouseEntryRowDto;
import com.kahga.pluse.housesentiment.dto.HouseReportFilter;
import com.kahga.pluse.housesentiment.dto.HouseReportResponse;
import com.kahga.pluse.housesentiment.service.HouseExcelReportGenerator;
import com.kahga.pluse.housesentiment.service.HousePdfReportGenerator;
import com.kahga.pluse.housesentiment.service.HouseReportService;
import com.kahga.pluse.location.service.LocationService;
import com.kahga.pluse.location.entity.UnitLevel;
import com.kahga.pluse.sentiment.entity.ConfidenceLevel;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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

/**
 * The admin's pre-election (house-level) report: the same summary and exports as
 * the named-voter report, over its own data.
 */
@RestController
@RequestMapping("/api/admin/house-sentiment")
@RequiredArgsConstructor
public class AdminHouseSentimentController {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final HouseReportService houseReportService;
    private final HousePdfReportGenerator pdfReportGenerator;
    private final HouseExcelReportGenerator excelReportGenerator;
    private final LocationService locationService;

    @GetMapping("/summary")
    public ApiResponse<HouseReportResponse> summary(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) UUID unitId,
            @RequestParam(required = false) String candidateId,
            @RequestParam(required = false) String confidence,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        return ApiResponse.ok(houseReportService.report(filter(level, unitId, candidateId, confidence, from, to)));
    }

    /** The houses behind one row of the report: everything recorded inside that unit. */
    @GetMapping("/houses")
    public ApiResponse<List<HouseEntryRowDto>> houses(
            @RequestParam UUID unitId,
            @RequestParam(required = false) String candidateId,
            @RequestParam(required = false) String confidence,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        return ApiResponse.ok(houseReportService.entries(filter(null, unitId, candidateId, confidence, from, to)));
    }

    /** The same drill-down as a file: every house in the unit, one per row. */
    @GetMapping("/houses/export")
    public ResponseEntity<byte[]> exportHouses(
            @RequestParam(defaultValue = "excel") String format,
            @RequestParam UUID unitId,
            @RequestParam(required = false) String candidateId,
            @RequestParam(required = false) String confidence,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        HouseReportFilter filter = filter(null, unitId, candidateId, confidence, from, to);
        List<HouseEntryRowDto> houses = houseReportService.entries(filter);
        String unitName = locationService.require(unitId).getName();

        boolean pdf = "pdf".equalsIgnoreCase(format);
        byte[] body = pdf
                ? pdfReportGenerator.generateEntries(houses, unitName, filter)
                : excelReportGenerator.generateEntries(houses, unitName, filter);
        String fileName = "k-pulse-houses-%s-%s.%s"
                .formatted(slug(unitName), LocalDate.now(), pdf ? "pdf" : "xlsx");

        return ResponseEntity.ok()
                .contentType(pdf ? MediaType.APPLICATION_PDF : MediaType.parseMediaType(XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(body);
    }

    /** Unit names carry spaces and Odia text; the filename stays plain ASCII. */
    private String slug(String unitName) {
        String cleaned = unitName.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return cleaned.isBlank() ? "unit" : cleaned;
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(defaultValue = "excel") String format,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) UUID unitId,
            @RequestParam(required = false) String candidateId,
            @RequestParam(required = false) String confidence,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        HouseReportFilter filter = filter(level, unitId, candidateId, confidence, from, to);
        HouseReportResponse report = houseReportService.report(filter);

        boolean pdf = "pdf".equalsIgnoreCase(format);
        byte[] body = pdf ? pdfReportGenerator.generate(report, filter) : excelReportGenerator.generate(report, filter);
        String fileName = "k-pulse-pre-election-%s-%s.%s"
                .formatted(filter.levelOrDefault().name().toLowerCase(), LocalDate.now(), pdf ? "pdf" : "xlsx");

        return ResponseEntity.ok()
                .contentType(pdf ? MediaType.APPLICATION_PDF : MediaType.parseMediaType(XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(body);
    }

    /** The SPA sends the literal "ALL" for an unset filter; treat that as null. */
    private HouseReportFilter filter(
            String level, UUID unitId, String candidateId, String confidence, Instant from, Instant to) {
        return new HouseReportFilter(
                enumOrNull(level, UnitLevel.class),
                unitId,
                uuidOrNull(candidateId),
                enumOrNull(confidence, ConfidenceLevel.class),
                from,
                to);
    }

    private <E extends Enum<E>> E enumOrNull(String value, Class<E> type) {
        return value == null || value.isBlank() || "ALL".equalsIgnoreCase(value) ? null : Enum.valueOf(type, value);
    }

    private UUID uuidOrNull(String value) {
        return value == null || value.isBlank() || "ALL".equalsIgnoreCase(value) ? null : UUID.fromString(value);
    }
}
