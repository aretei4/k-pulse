package com.kahga.pluse.accountdeletion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Filed from the public delete-account page. Only the registered mobile number
 * identifies the account; approval is what actually deletes anything, and an
 * admin checks who asked before granting it.
 */
public record CreateDeletionRequestDto(
        @NotBlank
                @Pattern(regexp = "\\d{10}", message = "Enter the 10-digit mobile number you registered with")
                String phone,
        @Size(max = 500) String reason) {}
