package com.kahga.pluse.sentiment.controller;

import com.kahga.pluse.common.response.ApiResponse;
import com.kahga.pluse.security.CurrentUserService;
import com.kahga.pluse.sentiment.dto.RecordSentimentRequest;
import com.kahga.pluse.sentiment.dto.SentimentEntryDto;
import com.kahga.pluse.sentiment.service.SentimentService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/voters")
@RequiredArgsConstructor
public class SentimentController {

    private final SentimentService sentimentService;
    private final CurrentUserService currentUserService;

    @PostMapping("/{voterId}/sentiment")
    public ApiResponse<SentimentEntryDto> record(
            @PathVariable UUID voterId, @Valid @RequestBody RecordSentimentRequest payload) {
        var entry = sentimentService.record(currentUserService.user(), voterId, payload);
        return ApiResponse.ok(SentimentEntryDto.from(entry));
    }
}
