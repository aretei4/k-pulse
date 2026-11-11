package com.kahga.pluse.user.controller;

import com.kahga.pluse.common.response.ApiResponse;
import com.kahga.pluse.security.CurrentUserService;
import com.kahga.pluse.user.dto.AgentPasswordStatusDto;
import com.kahga.pluse.user.dto.ChangePasswordRequest;
import com.kahga.pluse.user.service.AgentPasswordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FA-24: the agent's own sign-in settings. Always about the signed-in agent —
 * there is no id in any of these paths, so one agent cannot reach another's.
 */
@RestController
@RequestMapping("/api/agent/password")
@RequiredArgsConstructor
public class AgentPasswordController {

    private final AgentPasswordService agentPasswordService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public ApiResponse<AgentPasswordStatusDto> status() {
        return ApiResponse.ok(agentPasswordService.status(currentUserService.user()));
    }

    /** Asks the admin for a new password. The admin sets it; nothing is sent by SMS. */
    @PostMapping("/reset-request")
    public ApiResponse<AgentPasswordStatusDto> request() {
        return ApiResponse.ok(
                agentPasswordService.request(currentUserService.user()),
                "Your administrator will set a new password and give it to you");
    }

    @DeleteMapping("/reset-request")
    public ApiResponse<AgentPasswordStatusDto> withdraw() {
        return ApiResponse.ok(agentPasswordService.withdraw(currentUserService.user()), "Request withdrawn");
    }

    /** The agent choosing their own — voluntarily, or after an admin reset. */
    @PutMapping
    public ApiResponse<AgentPasswordStatusDto> change(@Valid @RequestBody ChangePasswordRequest request) {
        return ApiResponse.ok(
                agentPasswordService.change(currentUserService.user(), request), "Password changed");
    }
}
