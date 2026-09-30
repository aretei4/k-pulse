package com.kahga.pluse.voterchangerequest.dto;

import com.kahga.pluse.voterchangerequest.entity.VoterChangeRequest;
import com.kahga.pluse.voterchangerequest.entity.VoterChangeStatus;
import com.kahga.pluse.voterchangerequest.entity.VoterChangeType;
import java.time.Instant;
import java.util.UUID;

public record VoterChangeRequestDto(
        UUID id,
        VoterChangeType type,
        VoterChangeStatus status,
        UUID agentId,
        String agentName,
        UUID voterId,
        String voterName,
        UUID boothId,
        String unitPath,
        VoterChangePayload payload,
        Instant proposedAt,
        Instant decidedAt,
        String reviewerNote) {

    public static VoterChangeRequestDto from(VoterChangeRequest change) {
        return new VoterChangeRequestDto(
                change.getId(),
                change.getChangeType(),
                change.getStatus(),
                change.getAgent().getId(),
                change.getAgent().getName(),
                change.getVoter() == null ? null : change.getVoter().getId(),
                change.getVoterName(),
                change.getBooth().getId(),
                change.getBooth().getPath(),
                new VoterChangePayload(
                        change.getEpicNo(),
                        change.getName(),
                        change.getRelation(),
                        change.getHouseNo(),
                        change.getAge(),
                        change.getGender(),
                        change.getWardNo(),
                        change.getReason()),
                change.getProposedAt(),
                change.getDecidedAt(),
                change.getReviewerNote());
    }
}
