package com.kahga.pluse.agentreport.controller;

import com.kahga.pluse.agentreport.dto.AgentReportDto;
import com.kahga.pluse.agentreport.dto.AgentReportScopeDto;
import com.kahga.pluse.agentreport.service.AgentReportService;
import com.kahga.pluse.common.response.ApiResponse;
import com.kahga.pluse.security.CurrentUserService;
import com.kahga.pluse.sentiment.entity.ConfidenceLevel;
import com.kahga.pluse.voterexport.dto.SentimentFilter;
import com.kahga.pluse.voterexport.service.VoterExportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-U14. The agent is read from the token and never from a parameter, and
 * there is deliberately no endpoint here that takes a block or a district id:
 * the only area an agent can ask about is a panchayat they hold an approval
 * for, and even then the figures cover their approved booths alone.
 */
@RestController
@RequestMapping("/api/agent/reports")
@RequiredArgsConstructor
public class AgentReportController {

    private final AgentReportService agentReportService;
    private final CurrentUserService currentUserService;

    /** What the locked area box and the dropdowns are filled from (AG-2). */
    @GetMapping("/scope")
    public ApiResponse<List<AgentReportScopeDto>> scope() {
        return ApiResponse.ok(agentReportService.scope(currentUserService.user()));
    }

    /**
     * AG-9: the voters behind the drawn report, as a watermarked A4 list. The
     * same choices as the figures, so the file and the tiles agree.
     */
    @GetMapping(value = "/voters.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> voters(
            @RequestParam UUID panchayatId,
            @RequestParam(required = false) UUID candidateId,
            @RequestParam(required = false) List<UUID> booth,
            @RequestParam(required = false) List<SentimentFilter> sentiment,
            @RequestParam(required = false) List<ConfidenceLevel> confidence,
            @RequestParam(required = false) List<Integer> ward,
            @RequestParam(required = false) List<String> caste,
            @RequestParam(required = false) Integer days) {

        VoterExportService.Pdf pdf = agentReportService.exportVoters(
                currentUserService.user(), panchayatId, candidateId, booth, sentiment, confidence, ward, caste, days);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(pdf.fileName())
                                .build()
                                .toString())
                .body(pdf.bytes());
    }

    @GetMapping
    public ApiResponse<AgentReportDto> report(
            @RequestParam UUID panchayatId,
            @RequestParam(required = false) UUID candidateId,
            @RequestParam(required = false) List<UUID> booth,
            @RequestParam(required = false) List<ConfidenceLevel> confidence,
            @RequestParam(required = false) List<Integer> ward,
            @RequestParam(required = false) List<String> caste,
            @RequestParam(required = false) Integer days) {

        return ApiResponse.ok(agentReportService.report(
                currentUserService.user(), panchayatId, candidateId, booth, confidence, ward, caste, days));
    }
}
