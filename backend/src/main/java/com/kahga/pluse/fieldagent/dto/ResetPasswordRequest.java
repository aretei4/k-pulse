package com.kahga.pluse.fieldagent.dto;

import jakarta.validation.constraints.Size;

/**
 * FA-19: the admin may type an easy password of their own, or leave it out and
 * take the suggestion the server makes.
 */
public record ResetPasswordRequest(@Size(max = 72) String password) {}
