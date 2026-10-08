package com.kahga.pluse.auth.dto;

import com.kahga.pluse.user.dto.UserDto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Every auth payload in one place — they are all two or three fields. */
public final class AuthDtos {

    /** The length admins are already held to, so one rule covers every account. */
    public static final int MIN_PASSWORD_LENGTH = 8;

    private AuthDtos() {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record OtpRequest(@NotBlank @Pattern(regexp = "\\d{10}", message = "must be a 10-digit number") String phone) {}

    public record OtpVerifyRequest(
            @NotBlank @Pattern(regexp = "\\d{10}", message = "must be a 10-digit number") String phone,
            @NotBlank String otp) {}

    /**
     * The password is optional: agents registered before it existed sign in with
     * an OTP, and nothing here should force them to stop. Left out, the account
     * is created exactly as it was before, OTP only.
     */
    public record SignupRequest(
            @NotBlank String name,
            @NotBlank @Email String email,
            @NotBlank @Pattern(regexp = "\\d{10}", message = "must be a 10-digit number") String phone,
            String address,
            @Size(min = MIN_PASSWORD_LENGTH, message = "must be at least 8 characters") String password) {

        public boolean wantsPassword() {
            return password != null && !password.isBlank();
        }
    }

    public record AuthResponse(String token, UserDto user) {}

    public record OtpSentResponse(boolean sent, String devOtp) {}

    public record SignupResponse(String message, String devOtp) {}
}
