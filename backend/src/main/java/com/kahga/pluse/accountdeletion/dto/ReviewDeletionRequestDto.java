package com.kahga.pluse.accountdeletion.dto;

import jakarta.validation.constraints.Size;

public record ReviewDeletionRequestDto(@Size(max = 500) String note) {}
