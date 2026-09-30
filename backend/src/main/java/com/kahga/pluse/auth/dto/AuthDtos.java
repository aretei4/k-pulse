package com.kahga.pluse.auth.dto;

import com.kahga.pluse.user.dto.UserDto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Every auth payload in one place — they are all two or three fields. */
public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record OtpRequest(@NotBlank @Pattern(regexp = "\\d{10}", message = "must be a 10-digit number") String phone) {}

    public record OtpVerifyRequest(
            @NotBlank @Pattern(regexp = "\\d{10}", message = "must be a 10-digit number") String phone,
            @NotBlank String otp) {}

    public record SignupRequest(
            @NotBlank String name,
            @NotBlank @Email String email,
            @NotBlank @Pattern(regexp = "\\d{10}", message = "must be a 10-digit number") String phone,
            String address) {}

    public record AuthResponse(String token, UserDto user) {}

    public record OtpSentResponse(boolean sent, String devOtp) {}

    public record SignupResponse(String message, String devOtp) {}
}
