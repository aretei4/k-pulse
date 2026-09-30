package com.kahga.pluse.candidate.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/**
 * Adding or renaming a candidate. Party is optional — independents have none —
 * and so is the panchayat, for a candidate standing across the constituency.
 */
public record CandidateRequest(@NotBlank String name, String party, UUID unitId) {}
