package com.kahga.pluse.accessrequest.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateAccessRequestDto(@NotNull UUID unitId, @NotNull UUID candidateId) {}
