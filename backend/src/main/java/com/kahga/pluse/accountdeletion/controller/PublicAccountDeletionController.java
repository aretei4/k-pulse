package com.kahga.pluse.accountdeletion.controller;

import com.kahga.pluse.accountdeletion.dto.CreateDeletionRequestDto;
import com.kahga.pluse.accountdeletion.service.AccountDeletionService;
import com.kahga.pluse.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public delete-account page (Google Play requires a URL anyone can reach,
 * signed in or not). It only files a request — an admin approves before anything
 * is deleted — so it sits under the open {@code /api/auth} tree.
 */
@RestController
@RequestMapping("/api/auth/account-deletion")
@RequiredArgsConstructor
public class PublicAccountDeletionController {

    private final AccountDeletionService accountDeletionService;

    @PostMapping
    public ApiResponse<Void> request(@Valid @RequestBody CreateDeletionRequestDto payload) {
        accountDeletionService.request(payload);
        // Always the same answer, so the page cannot be used to test whether a
        // number is registered.
        return ApiResponse.ok(
                null, "If that number has an account, an administrator will review the request and confirm by phone.");
    }
}
