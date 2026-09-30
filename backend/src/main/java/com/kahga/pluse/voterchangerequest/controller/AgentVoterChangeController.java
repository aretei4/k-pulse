package com.kahga.pluse.voterchangerequest.controller;

import com.kahga.pluse.common.response.ApiResponse;
import com.kahga.pluse.security.CurrentUserService;
import com.kahga.pluse.voterchangerequest.dto.ProposeVoterChangeRequest;
import com.kahga.pluse.voterchangerequest.dto.VoterChangeRequestDto;
import com.kahga.pluse.voterchangerequest.service.VoterChangeRequestService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/voter-changes")
@RequiredArgsConstructor
public class AgentVoterChangeController {

    private final VoterChangeRequestService service;
    private final CurrentUserService currentUserService;

    @GetMapping
    public ApiResponse<List<VoterChangeRequestDto>> mine() {
        return ApiResponse.ok(service.listForAgent(currentUserService.user().getId()).stream()
                .map(VoterChangeRequestDto::from)
                .toList());
    }

    @PostMapping
    public ApiResponse<VoterChangeRequestDto> propose(@Valid @RequestBody ProposeVoterChangeRequest payload) {
        return ApiResponse.ok(VoterChangeRequestDto.from(service.propose(currentUserService.user(), payload)));
    }
}
