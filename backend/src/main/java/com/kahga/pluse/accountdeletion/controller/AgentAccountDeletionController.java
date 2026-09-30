package com.kahga.pluse.accountdeletion.controller;

import com.kahga.pluse.accountdeletion.dto.AccountDeletionRequestDto;
import com.kahga.pluse.accountdeletion.dto.ReviewDeletionRequestDto;
import com.kahga.pluse.accountdeletion.service.AccountDeletionService;
import com.kahga.pluse.common.response.ApiResponse;
import com.kahga.pluse.security.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The in-app route to the same queue, for an agent who is already signed in. */
@RestController
@RequestMapping("/api/agent/account-deletion")
@RequiredArgsConstructor
public class AgentAccountDeletionController {

    private final AccountDeletionService accountDeletionService;
    private final CurrentUserService currentUserService;

    @PostMapping
    public ApiResponse<AccountDeletionRequestDto> request(@Valid @RequestBody ReviewDeletionRequestDto payload) {
        var request = accountDeletionService.requestFor(currentUserService.user(), payload.note());
        return ApiResponse.ok(
                AccountDeletionRequestDto.from(request),
                "An administrator will review this and confirm before your account is deleted.");
    }
}
