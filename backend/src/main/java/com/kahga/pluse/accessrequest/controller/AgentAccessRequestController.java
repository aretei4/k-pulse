package com.kahga.pluse.accessrequest.controller;

import com.kahga.pluse.accessrequest.dto.AccessRequestDto;
import com.kahga.pluse.accessrequest.dto.AgentBoothDto;
import com.kahga.pluse.accessrequest.dto.CreateAccessRequestDto;
import com.kahga.pluse.accessrequest.service.AccessRequestService;
import com.kahga.pluse.common.response.ApiResponse;
import com.kahga.pluse.security.CurrentUserService;
import com.kahga.pluse.user.entity.User;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentAccessRequestController {

    private final AccessRequestService accessRequestService;
    private final CurrentUserService currentUserService;

    @GetMapping("/access-requests")
    public ApiResponse<List<AccessRequestDto>> mine() {
        User agent = currentUserService.user();
        return ApiResponse.ok(
                accessRequestService.listForAgent(agent.getId()).stream().map(AccessRequestDto::from).toList());
    }

    @PostMapping("/access-requests")
    public ApiResponse<AccessRequestDto> create(@Valid @RequestBody CreateAccessRequestDto payload) {
        User agent = currentUserService.user();
        return ApiResponse.ok(AccessRequestDto.from(accessRequestService.create(agent, payload)));
    }

    @GetMapping("/booths")
    public ApiResponse<List<AgentBoothDto>> booths() {
        return ApiResponse.ok(accessRequestService.accessibleBooths(currentUserService.user().getId()));
    }
}
