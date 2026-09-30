package com.kahga.pluse.user.entity;

public enum Role {
    /** Unrestricted: every unit, plus the only role that can make admin accounts (FR-A13). */
    SUPER_ADMIN,
    /** Scoped to one district, block or panchayat — see {@link AccessScope}. */
    ADMIN,
    FIELD_AGENT;

    public boolean isAdminKind() {
        return this == SUPER_ADMIN || this == ADMIN;
    }
}
