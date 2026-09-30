package com.kahga.pluse.voter.entity;

import com.kahga.pluse.location.entity.Unit;
import com.kahga.pluse.user.entity.User;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Audit row for each Excel roll import. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoterUpload {

    private UUID id;

    private String fileName;

    private Unit unit;

    private int rowCount;

    private String status;

    private String message;

    private User uploadedBy;

    private Instant uploadedAt;
}
