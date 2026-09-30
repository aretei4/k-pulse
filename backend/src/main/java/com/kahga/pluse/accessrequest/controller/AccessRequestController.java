package com.kahga.pluse.accessrequest.controller;

import com.kahga.pluse.accessrequest.dto.AccessRequestDto;
import com.kahga.pluse.accessrequest.dto.ReviewAccessRequestDto;
import com.kahga.pluse.accessrequest.entity.AccessRequestStatus;
import com.kahga.pluse.accessrequest.service.AccessRequestService;
import com.kahga.pluse.common.response.ApiResponse;
import jakarta.validation.Valid;
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
@RequestMapping("/api/admin/access-requests")
@RequiredArgsConstructor
public class AccessRequestController {

    private final AccessRequestService accessRequestService;

    @GetMapping
    public ApiResponse<List<AccessRequestDto>> list(@RequestParam(required = false) String status) {
        AccessRequestStatus filter = status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)
                ? null
                : AccessRequestStatus.valueOf(status);
        return ApiResponse.ok(accessRequestService.list(filter).stream().map(AccessRequestDto::from).toList());
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<AccessRequestDto> approve(
            @PathVariable UUID id, @Valid @RequestBody(required = false) ReviewAccessRequestDto body) {
        Integer months = body == null ? null : body.months();
        String note = body == null ? null : body.note();
        return ApiResponse.ok(AccessRequestDto.from(accessRequestService.approve(id, months, note)));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<AccessRequestDto> reject(
            @PathVariable UUID id, @RequestBody(required = false) ReviewAccessRequestDto body) {
        return ApiResponse.ok(AccessRequestDto.from(accessRequestService.reject(id, body == null ? null : body.note())));
    }

    @PostMapping("/{id}/revoke")
    public ApiResponse<AccessRequestDto> revoke(@PathVariable UUID id) {
        return ApiResponse.ok(AccessRequestDto.from(accessRequestService.revoke(id)));
    }
}
