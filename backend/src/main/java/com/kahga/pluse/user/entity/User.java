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

    private String passwordHash;

    private Role role;

    /** Set on ADMIN accounts only; null for a SUPER_ADMIN or a field agent (FR-A9). */
    private AccessScope scope;

    private boolean active;

    private Instant createdAt;
}
