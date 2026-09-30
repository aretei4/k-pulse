package com.kahga.pluse.housesentiment.controller;

import com.kahga.pluse.common.response.ApiResponse;
import com.kahga.pluse.housesentiment.dto.HouseInsightsDto;
import com.kahga.pluse.housesentiment.dto.HouseSentimentEntryDto;
import com.kahga.pluse.housesentiment.dto.RecordHouseSentimentRequest;
import com.kahga.pluse.housesentiment.service.HouseSentimentService;
import com.kahga.pluse.security.CurrentUserService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Agent-only, like the rest of {@code /api/agent/**} (see SecurityConfig). */
@RestController
@RequestMapping("/api/agent/houses")
@RequiredArgsConstructor
public class HouseSentimentController {

    private final HouseSentimentService houseSentimentService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public ApiResponse<List<HouseSentimentEntryDto>> list(
            @RequestParam UUID boothId, @RequestParam UUID candidateId) {
        List<HouseSentimentEntryDto> entries =
                houseSentimentService.list(currentUserService.user(), boothId, candidateId).stream()
                        .map(HouseSentimentEntryDto::from)
                        .toList();
        return ApiResponse.ok(entries);
    }

    /** Booth-wise totals for the pre-election charts — houses, not voters. */
    @GetMapping("/insights")
    public ApiResponse<HouseInsightsDto> insights(
            @RequestParam(required = false) UUID boothId, @RequestParam(required = false) UUID candidateId) {
        return ApiResponse.ok(houseSentimentService.insights(currentUserService.user(), boothId, candidateId));
    }

    @PostMapping
    public ApiResponse<HouseSentimentEntryDto> record(@Valid @RequestBody RecordHouseSentimentRequest payload) {
        var entry = houseSentimentService.record(currentUserService.user(), payload);
        return ApiResponse.ok(HouseSentimentEntryDto.from(entry));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        houseSentimentService.delete(currentUserService.user(), id);
        return ApiResponse.ok(null);
    }
}
