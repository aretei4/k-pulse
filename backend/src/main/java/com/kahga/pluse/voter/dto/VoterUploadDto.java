package com.kahga.pluse.voter.dto;

import com.kahga.pluse.location.entity.UnitLevel;
import com.kahga.pluse.voter.entity.VoterUpload;
import java.time.Instant;
import java.util.UUID;

public record VoterUploadDto(
        UUID id,
        String fileName,
        UnitLevel unitLevel,
        String unitName,
        int rowCount,
        Instant uploadedAt,
        String uploadedByName,
        String status,
        String message) {

    public static VoterUploadDto from(VoterUpload upload) {
        return new VoterUploadDto(
                upload.getId(),
                upload.getFileName(),
                upload.getUnit().getLevel(),
                upload.getUnit().getName(),
                upload.getRowCount(),
                upload.getUploadedAt(),
                upload.getUploadedBy().getName(),
                upload.getStatus(),
                upload.getMessage());
    }
}
