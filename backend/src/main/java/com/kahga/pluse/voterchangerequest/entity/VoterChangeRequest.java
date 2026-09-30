package com.kahga.pluse.voterchangerequest.entity;

import com.kahga.pluse.location.entity.Unit;
import com.kahga.pluse.user.entity.User;
import com.kahga.pluse.voter.entity.Gender;
import com.kahga.pluse.voter.entity.Voter;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An agent's proposal to add, edit or delete a voter. The proposed field values
 * live here as plain columns — nothing is written to `voter` until an admin
 * approves (FR-A8 / FR-U11).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoterChangeRequest {

    private UUID id;

    private VoterChangeType changeType;

    private VoterChangeStatus status;

    private User agent;

    /** Only the id is populated when read back; null once the voter has been deleted. */
    private Voter voter;

    /** Kept alongside the FK so the proposal still reads correctly after a delete. */
    private String voterName;

    private Unit booth;

    private String epicNo;

    private String name;

    private String relation;

    private String houseNo;

    private Integer age;

    private Gender gender;

    private Integer wardNo;

    private String reason;

    private Instant proposedAt;

    private Instant decidedAt;

    private String reviewerNote;
}
