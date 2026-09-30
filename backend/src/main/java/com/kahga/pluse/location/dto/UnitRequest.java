package com.kahga.pluse.location.dto;

import com.kahga.pluse.location.entity.UnitLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Adding or editing one unit of the District > Block > Panchayat > Booth tree.
 * {@code parentId} is null only for a district; every other level sits under the
 * level directly above it.
 */
public record UnitRequest(@NotNull UnitLevel level, @NotBlank String name, UUID parentId) {}
