package com.kahga.pluse.auth.entity;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A row of {@code otp_token}. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpToken {

    private UUID id;

    private String phone;

    private String code;

    private Instant expiresAt;

    private boolean consumed;

    private Instant createdAt;
}
