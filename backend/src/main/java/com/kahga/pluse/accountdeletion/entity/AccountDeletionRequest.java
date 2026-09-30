package com.kahga.pluse.accountdeletion.entity;

import com.kahga.pluse.user.entity.User;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An agent's request to have their account removed. The agent's name, phone and
 * email are copied onto the row: once an admin approves, the account is gone and
 * this row is the only remaining record of what was asked for and granted.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountDeletionRequest {

    private UUID id;

    /** Null once the account has been deleted. */
    private User user;

    private String agentName;

    private String agentPhone;

    private String agentEmail;

    private String reason;

    private DeletionStatus status;

    private Instant requestedAt;

    private Instant reviewedAt;

    private User reviewedBy;

    private String reviewerNote;

    /** How many recorded entries the approval erased. */
    private Integer deletedEntries;
}
