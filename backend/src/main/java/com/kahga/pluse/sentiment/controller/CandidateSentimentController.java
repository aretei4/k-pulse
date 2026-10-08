package com.kahga.pluse.sentiment.controller;

import com.kahga.pluse.candidate.dto.CandidateDto;
import com.kahga.pluse.common.response.ApiResponse;
import com.kahga.pluse.location.entity.UnitLevel;
import com.kahga.pluse.sentiment.dto.CandidateSentimentDto;
import com.kahga.pluse.sentiment.dto.CandidateSentimentFilter;
import com.kahga.pluse.sentiment.dto.CycleComparisonDto;
import com.kahga.pluse.sentiment.dto.SentimentSource;
import com.kahga.pluse.sentiment.service.CandidateSentimentService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** FR-A15: the candidate sentiment screen. Read-only, and scoped like every admin query. */
@RestController
@RequestMapping("/api/admin/candidate-sentiment")
@RequiredArgsConstructor
public class CandidateSentimentController {

    private final CandidateSentimentService candidateSentimentService;

    /** Only candidates with something recorded in the caller's area. */
    @GetMapping("/candidates")
    public ApiResponse<List<CandidateDto>> candidates() {
        return ApiResponse.ok(
                candidateSentimentService.candidatesInScope().stream().map(CandidateDto::from).toList());
    }

    @GetMapping("/{candidateId}")
    public ApiResponse<CandidateSentimentDto> report(
            @PathVariable UUID candidateId,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) UUID parentUnitId,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) UUID cycleId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        return ApiResponse.ok(candidateSentimentService.report(
                candidateId, filter(level, parentUnitId, source, cycleId, from, to)));
    }

    /** FR-A11: the same unit across every cycle, for a trend rather than a snapshot. */
    @GetMapping("/{candidateId}/cycles")
    public ApiResponse<List<CycleComparisonDto>> acrossCycles(
            @PathVariable UUID candidateId,
            @RequestParam(required = false) UUID parentUnitId,
            @RequestParam(required = false) String source) {

        return ApiResponse.ok(candidateSentimentService.acrossCycles(
                candidateId, filter(null, parentUnitId, source, null, null, null)));
    }

    private CandidateSentimentFilter filter(
            String level, UUID parentUnitId, String source, UUID cycleId, Instant from, Instant to) {
        return new CandidateSentimentFilter(
                enumOrNull(level, UnitLevel.class),
                parentUnitId,
                enumOrNull(source, SentimentSource.class),
                cycleId,
                from,
                to);
    }

    /** The SPA sends the literal "ALL" for an unset filter; treat that as null. */
    private <E extends Enum<E>> E enumOrNull(String value, Class<E> type) {
        return value == null || value.isBlank() || "ALL".equalsIgnoreCase(value) ? null : Enum.valueOf(type, value);
    }
}
