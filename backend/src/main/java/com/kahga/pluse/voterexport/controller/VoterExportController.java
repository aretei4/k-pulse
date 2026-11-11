package com.kahga.pluse.voterexport.controller;

import com.kahga.pluse.common.response.ApiResponse;
import com.kahga.pluse.security.CurrentUserService;
import com.kahga.pluse.user.entity.User;
import com.kahga.pluse.voterexport.dto.SentimentFilter;
import com.kahga.pluse.voterexport.dto.VoterExportSummaryDto;
import com.kahga.pluse.voterexport.service.VoterExportService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-U13. Both endpoints answer for the signed-in agent only: the agent is read
 * from the token, never from a parameter, and SecurityConfig has already
 * refused anyone who is not a field agent.
 */
@RestController
@RequestMapping("/api/agent/voter-list")
@RequiredArgsConstructor
public class VoterExportController {

    private final VoterExportService voterExportService;
    private final CurrentUserService currentUserService;

    /** Counts and the preview behind the Download page. Nothing is built here. */
    @GetMapping("/export/count")
    public ApiResponse<VoterExportSummaryDto> count(
            @RequestParam UUID unitId,
            @RequestParam(required = false) UUID candidateId,
            @RequestParam(name = "sentiment", required = false) List<String> sentiment) {

        User agent = currentUserService.user();
        Set<SentimentFilter> chosen = parse(sentiment);
        return ApiResponse.ok(voterExportService.summary(agent, unitId, candidateId, chosen));
    }

    /**
     * The file. Returned inline on the wire as bytes with a file name; the phone
     * shell is what saves it to Downloads.
     */
    @GetMapping(value = "/export.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> export(
            @RequestParam UUID unitId,
            @RequestParam(required = false) UUID candidateId,
            @RequestParam(name = "sentiment") List<String> sentiment) {

        User agent = currentUserService.user();
        VoterExportService.Pdf pdf =
                voterExportService.export(agent, unitId, candidateId, SentimentFilter.parse(sentiment));

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

    /**
     * The count endpoint is also what the page calls before anything is chosen,
     * so an empty selection is a count of nothing rather than a refusal.
     */
    private Set<SentimentFilter> parse(List<String> sentiment) {
        boolean empty = sentiment == null
                || sentiment.stream().allMatch(part -> part == null || part.isBlank());
        return empty ? Set.of() : SentimentFilter.parse(sentiment);
    }
}
