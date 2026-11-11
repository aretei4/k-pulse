package com.kahga.pluse.fieldagent.dto;

/**
 * The one and only time the password is readable. It is shown to the admin to
 * pass on, and only its hash is kept.
 */
public record ResetPasswordResult(String temporaryPassword, int validDays) {}
