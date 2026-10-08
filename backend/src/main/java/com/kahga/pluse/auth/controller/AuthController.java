package com.kahga.pluse.auth.controller;

import com.kahga.pluse.auth.dto.AuthDtos;
import com.kahga.pluse.auth.service.AuthService;
import com.kahga.pluse.common.response.ApiResponse;
import com.kahga.pluse.security.CurrentUserService;
import com.kahga.pluse.user.dto.UserDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CurrentUserService currentUserService;

    @PostMapping("/admin/login")
    public ApiResponse<AuthDtos.AuthResponse> adminLogin(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return ApiResponse.ok(authService.adminLogin(request));
    }

    /** The email and password route, for agents who set one when they signed up. */
    @PostMapping("/agent/login")
    public ApiResponse<AuthDtos.AuthResponse> agentLogin(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return ApiResponse.ok(authService.agentLogin(request));
    }

    @PostMapping("/agent/otp/request")
    public ApiResponse<AuthDtos.OtpSentResponse> requestOtp(@Valid @RequestBody AuthDtos.OtpRequest request) {
        return ApiResponse.ok(authService.requestOtp(request.phone()));
    }

    @PostMapping("/agent/otp/verify")
    public ApiResponse<AuthDtos.AuthResponse> verifyOtp(@Valid @RequestBody AuthDtos.OtpVerifyRequest request) {
        return ApiResponse.ok(authService.verifyOtp(request));
    }

    @PostMapping("/agent/signup")
    public ApiResponse<AuthDtos.SignupResponse> signup(@Valid @RequestBody AuthDtos.SignupRequest request) {
        return ApiResponse.ok(authService.signup(request));
    }

    /** Stateless JWTs, so this is a client-side clear — kept so the SPA has one call to make. */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.ok(null);
    }

    @GetMapping("/me")
    public ApiResponse<UserDto> me() {
        return ApiResponse.ok(UserDto.from(currentUserService.user()));
    }
}
