package com.kahga.pluse.voterchangerequest.controller;

import com.kahga.pluse.common.response.ApiResponse;
import com.kahga.pluse.voterchangerequest.dto.ReviewVoterChangeRequest;
import com.kahga.pluse.voterchangerequest.dto.VoterChangeRequestDto;
import com.kahga.pluse.voterchangerequest.entity.VoterChangeStatus;
import com.kahga.pluse.voterchangerequest.service.VoterChangeRequestService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/voter-changes")
@RequiredArgsConstructor
public class VoterChangeRequestController {

    private final VoterChangeRequestService service;

    @GetMapping
    public ApiResponse<List<VoterChangeRequestDto>> list(@RequestParam(required = false) String status) {
        VoterChangeStatus filter = status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)
                ? null
                : VoterChangeStatus.valueOf(status);
        return ApiResponse.ok(service.list(filter).stream().map(VoterChangeRequestDto::from).toList());
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<VoterChangeRequestDto> approve(
            @PathVariable UUID id, @RequestBody(required = false) ReviewVoterChangeRequest body) {
        return ApiResponse.ok(VoterChangeRequestDto.from(service.approve(id, body == null ? null : body.note())));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<VoterChangeRequestDto> reject(
            @PathVariable UUID id, @RequestBody(required = false) ReviewVoterChangeRequest body) {
        return ApiResponse.ok(VoterChangeRequestDto.from(service.reject(id, body == null ? null : body.note())));
    }
}
