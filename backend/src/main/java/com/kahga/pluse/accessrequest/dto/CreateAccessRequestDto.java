package com.kahga.pluse.accessrequest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * One panchayat and one candidate. The candidate arrives as a name rather than
 * an id because the agent in the field knows who they are canvassing for, and
 * may be asking before anyone has added that candidate to the master list.
 */
public record CreateAccessRequestDto(@NotNull UUID unitId, @NotBlank String candidateName) {}
