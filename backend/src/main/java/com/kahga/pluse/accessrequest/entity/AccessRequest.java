package com.kahga.pluse.accessrequest.entity;

import com.kahga.pluse.candidate.entity.Candidate;
import com.kahga.pluse.location.entity.Unit;
import com.kahga.pluse.user.entity.User;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One agent + one unit + one candidate. An agent may hold several of these at
 * once; sentiment recorded under a grant is always tied to its candidate.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessRequest {

    private UUID id;

    private User agent;

    private Unit unit;

    private Candidate candidate;

    private AccessRequestStatus status;

    private Instant requestedAt;

    private Instant decidedAt;

    private Instant expiresAt;

    private String reviewerNote;

    public boolean isLive() {
        return status == AccessRequestStatus.APPROVED
                && (expiresAt == null || expiresAt.isAfter(Instant.now()));
    }
}
