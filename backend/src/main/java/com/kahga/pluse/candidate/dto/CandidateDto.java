package com.kahga.pluse.candidate.dto;

import com.kahga.pluse.candidate.entity.Candidate;
import java.util.UUID;

public record CandidateDto(UUID id, String name, String party, UUID unitId, String unitName) {

    public static CandidateDto from(Candidate candidate) {
        return new CandidateDto(
                candidate.getId(),
                candidate.getName(),
                candidate.getParty(),
                candidate.getUnit() == null ? null : candidate.getUnit().getId(),
                candidate.getUnit() == null ? null : candidate.getUnit().getName());
    }
}
