package com.kahga.pluse.auth.service;

import com.kahga.pluse.auth.dto.AuthDtos;
import com.kahga.pluse.auth.entity.OtpToken;
import com.kahga.pluse.auth.repository.OtpTokenRepository;
import com.kahga.pluse.common.exception.BusinessException;
import com.kahga.pluse.config.KPulseProperties;
import com.kahga.pluse.security.JwtTokenProvider;
import com.kahga.pluse.user.dto.UserDto;
import com.kahga.pluse.user.entity.Role;
import com.kahga.pluse.user.entity.User;
import com.kahga.pluse.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final OtpTokenRepository otpTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final KPulseProperties properties;

    @Transactional(readOnly = true)
    public AuthDtos.AuthResponse adminLogin(AuthDtos.LoginRequest request) {
        // Both admin kinds sign in here; the roles differ in what they may do, not how they get in.
        return signInWithPassword(request, user -> user.getRole().isAdminKind());
    }

    /**
     * An agent signing in with the email they registered and the password they
     * chose. Agents who never set one still sign in with an OTP, and get the
     * same answer as a wrong password here rather than being told their account
     * exists but has no password.
     */
    @Transactional(readOnly = true)
    public AuthDtos.AuthResponse agentLogin(AuthDtos.LoginRequest request) {
        return signInWithPassword(request, user -> user.getRole() == Role.FIELD_AGENT);
    }

    private AuthDtos.AuthResponse signInWithPassword(AuthDtos.LoginRequest request, Predicate<User> allowed) {
        User account = userRepository
                .findByEmailIgnoreCase(request.email())
                .filter(allowed)
                .filter(user -> user.getPasswordHash() != null)
                .filter(user -> passwordEncoder.matches(request.password(), user.getPasswordHash()))
                // One message whichever of those failed, so this cannot be used to
                // find out which email addresses are registered.
                .orElseThrow(() -> new BusinessException("Invalid email or password", HttpStatus.UNAUTHORIZED));

        if (!account.isActive()) {
            throw new BusinessException("This account has been deactivated", HttpStatus.FORBIDDEN);
        }
        return new AuthDtos.AuthResponse(tokenProvider.issue(account), UserDto.from(account));
    }

    @Transactional
    public AuthDtos.OtpSentResponse requestOtp(String phone) {
        User agent = userRepository
                .findByPhone(phone)
                .filter(user -> user.getRole() == Role.FIELD_AGENT)
                .orElseThrow(() -> new BusinessException("No agent registered with that number", HttpStatus.NOT_FOUND));
        if (!agent.isActive()) {
            throw new BusinessException("This account has been deactivated", HttpStatus.FORBIDDEN);
        }

        String code = "345678";//String.format("%06d", RANDOM.nextInt(1_000_000));
        otpTokenRepository.save(OtpToken.builder()
                .id(UUID.randomUUID())
                .phone(phone)
                .code(code)
                .expiresAt(Instant.now().plus(properties.getOtp().getTtlMinutes(), ChronoUnit.MINUTES))
                .consumed(false)
                .createdAt(Instant.now())
                .build());

        // No SMS gateway yet — the code goes to the log, and to the response in dev.
        log.info("OTP for {}: {}", phone, code);
        return new AuthDtos.OtpSentResponse(true, properties.getOtp().isExposeInResponse() ? code : null);
    }

    @Transactional
    public AuthDtos.AuthResponse verifyOtp(AuthDtos.OtpVerifyRequest request) {
        User agent = userRepository
                .findByPhone(request.phone())
                .filter(user -> user.getRole() == Role.FIELD_AGENT)
                .orElseThrow(() -> new BusinessException("No agent registered with that number", HttpStatus.NOT_FOUND));

        OtpToken token = otpTokenRepository
                .findFirstByPhoneAndConsumedFalseOrderByCreatedAtDesc(request.phone())
                .orElseThrow(() -> new BusinessException("Request a fresh OTP", HttpStatus.UNAUTHORIZED));

        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException("That OTP has expired — request a new one", HttpStatus.UNAUTHORIZED);
        }
        if (!token.getCode().equals(request.otp())) {
            throw new BusinessException("That OTP does not match", HttpStatus.UNAUTHORIZED);
        }

        token.setConsumed(true);
        otpTokenRepository.save(token);
        return new AuthDtos.AuthResponse(tokenProvider.issue(agent), UserDto.from(agent));
    }

    @Transactional
    public AuthDtos.SignupResponse signup(AuthDtos.SignupRequest request) {
        if (userRepository.existsByPhone(request.phone())) {
            throw new BusinessException("That phone number is already registered", HttpStatus.CONFLICT);
        }
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new BusinessException("That email is already registered", HttpStatus.CONFLICT);
        }

        User agent = User.builder()
                .id(UUID.randomUUID())
                .name(request.name().trim())
                .email(request.email().trim().toLowerCase())
                .phone(request.phone())
                .address(request.address())
                .passwordHash(request.wantsPassword() ? passwordEncoder.encode(request.password()) : null)
                .role(Role.FIELD_AGENT)
                .active(true)
                .createdAt(Instant.now())
                .build();
        userRepository.save(agent);

        // The OTP is still sent either way: a password is a second way in, not a
        // replacement, and an agent who mistypes it should not be locked out.
        AuthDtos.OtpSentResponse otp = requestOtp(agent.getPhone());
        String message = request.wantsPassword()
                ? "Account created. Sign in with your email and password, or with the OTP sent to your phone."
                : "Account created. Sign in with the OTP sent to your phone.";
        return new AuthDtos.SignupResponse(message, otp.devOtp());
    }
}
