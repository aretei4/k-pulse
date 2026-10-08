package com.kahga.pluse.electioncycle.dto;

import com.kahga.pluse.electioncycle.entity.ElectionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Opening the next cycle closes the one before it — only one is ever open. */
public record CreateElectionCycleRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull ElectionType electionType,
        @Min(2000) @Max(2100) int year) {}
