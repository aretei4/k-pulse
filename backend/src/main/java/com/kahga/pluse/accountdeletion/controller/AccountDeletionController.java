package com.kahga.pluse.accountdeletion.controller;

import com.kahga.pluse.accountdeletion.dto.AccountDeletionRequestDto;
import com.kahga.pluse.accountdeletion.dto.ReviewDeletionRequestDto;
import com.kahga.pluse.accountdeletion.entity.DeletionStatus;
import com.kahga.pluse.accountdeletion.service.AccountDeletionService;
import com.kahga.pluse.common.response.ApiResponse;
import com.kahga.pluse.security.CurrentUserService;
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

/** The admin's review queue. Approving here is what actually deletes an account. */
@RestController
@RequestMapping("/api/admin/account-deletions")
@RequiredArgsConstructor
public class AccountDeletionController {

    private final AccountDeletionService accountDeletionService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public ApiResponse<List<AccountDeletionRequestDto>> list(
            @RequestParam(required = false) DeletionStatus status) {
        return ApiResponse.ok(accountDeletionService.list(status).stream()
                .map(AccountDeletionRequestDto::from)
                .toList());
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<AccountDeletionRequestDto> approve(
            @PathVariable UUID id, @Valid @RequestBody(required = false) ReviewDeletionRequestDto payload) {
        var request = accountDeletionService.approve(
                currentUserService.user(), id, payload == null ? null : payload.note());
        return ApiResponse.ok(
                AccountDeletionRequestDto.from(request),
                "Account deleted with " + request.getDeletedEntries() + " recorded entr(ies).");
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<AccountDeletionRequestDto> reject(
            @PathVariable UUID id, @Valid @RequestBody(required = false) ReviewDeletionRequestDto payload) {
        var request =
                accountDeletionService.reject(currentUserService.user(), id, payload == null ? null : payload.note());
        return ApiResponse.ok(AccountDeletionRequestDto.from(request));
    }
}
