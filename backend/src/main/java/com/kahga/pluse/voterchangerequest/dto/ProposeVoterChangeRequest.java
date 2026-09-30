package com.kahga.pluse.voterchangerequest.dto;

import com.kahga.pluse.voterchangerequest.entity.VoterChangeType;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ProposeVoterChangeRequest(
        @NotNull VoterChangeType type, UUID voterId, @NotNull UUID boothId, @NotNull VoterChangePayload payload) {}
