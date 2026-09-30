package com.kahga.pluse.accessrequest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** `months` is only read on approve; null falls back to the configured 6. */
public record ReviewAccessRequestDto(@Min(1) @Max(60) Integer months, String note) {}
