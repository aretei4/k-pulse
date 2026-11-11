package com.kahga.pluse.user.entity;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A row of {@code app_user} — admins and field agents share the table. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    private UUID id;

    private String name;

    private String email;

    private String phone;

    private String address;

    /**
     * Who the agent says sent them, given at sign-up and optional: a mobile
     * number, a worker's code, or nothing. Kept as it was typed rather than
     * resolved to an account -- the app cannot check the claim, and a wrong
     * link would read as one it had.
     */
    private String referralNo;

    private String passwordHash;

    private Role role;

    /** Set on ADMIN accounts only; null for a SUPER_ADMIN or a field agent (FR-A9). */
    private AccessScope scope;

    private boolean active;

    /**
     * FR-U13: whether this field agent may download a booth's voter list as a
     * PDF. Off until an admin switches it on, and meaningless on an admin
     * account, which has no agent screens.
     */
    private boolean downloadEnabled;

    /**
     * FR-A20: the readable agent id the admin's Field agents page shows and
     * searches on, rendered A-00272. Numbered on first save and never reused;
     * null on an admin account, which is not an agent.
     */
    private Integer agentNo;

    /**
     * FA-25: set when an admin resets the password, cleared when the agent
     * chooses their own. While it is true the agent is sent to the
     * change-password screen before anything else.
     */
    private boolean mustChangePassword;

    /** FA-24: when the agent asked for a new password; null when nobody is waiting. */
    private Instant passwordResetRequestedAt;

    /**
     * FR-A22: whether this agent has the Reports page in the app. Off until an
     * admin gives it, and checked again on every Reports call -- the phone only
     * shows or hides the tab.
     */
    private boolean reportsEnabled;

    private UUID reportsUpdatedBy;

    private Instant reportsUpdatedAt;

    /** Who last reset this password, and when the temporary one starts ageing. */
    private UUID loginUpdatedBy;

    private Instant loginUpdatedAt;

    private Instant createdAt;
}
