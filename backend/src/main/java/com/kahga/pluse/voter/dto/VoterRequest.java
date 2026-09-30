package com.kahga.pluse.voter.dto;

import com.kahga.pluse.voter.entity.Gender;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/** Admin add/edit payload — the agent's proposal carries the same fields. */
public record VoterRequest(
        UUID boothId,
        String epicNo,
        @NotBlank String name,
        String relation,
        String houseNo,
        @Min(18) @Max(120) int age,
        Gender gender,
        @Min(1) @Max(200) Integer wardNo) {}
