package com.kahga.pluse.user.controller;

import com.kahga.pluse.common.response.ApiResponse;
import com.kahga.pluse.user.dto.UpdateDownloadEnabledRequest;
import com.kahga.pluse.user.dto.UserDto;
import com.kahga.pluse.user.service.UserService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DL-13: the per-agent PDF download switch. Its own path, as the requirement
 * names it; SecurityConfig's /api/admin/** rule covers it, and the scope check
 * is in the service, where a tampered agent id cannot get round it.
 */
@RestController
@RequestMapping("/api/admin/agents")
@RequiredArgsConstructor
public class AgentDownloadSwitchController {

    private final UserService userService;

    @PutMapping("/{agentId}/download-enabled")
    public ApiResponse<UserDto> setDownloadEnabled(
            @PathVariable UUID agentId, @RequestBody UpdateDownloadEnabledRequest request) {
        return ApiResponse.ok(UserDto.from(userService.setDownloadEnabled(agentId, request.enabled())));
    }
}
