package com.kahga.pluse.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kahga.pluse.auth.dto.AuthDtos;
import com.kahga.pluse.auth.service.AuthService;
import com.kahga.pluse.common.exception.BusinessException;
import com.kahga.pluse.user.entity.Role;
import com.kahga.pluse.user.entity.User;
import com.kahga.pluse.user.repository.UserRepository;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * An agent may now choose a password at signup and sign in with their email.
 * The OTP route has to keep working regardless: agents registered before this
 * existed have no password, and a password is a second way in rather than a
 * replacement.
 */
@SpringBootTest
@ActiveProfiles("test")
class AgentPasswordTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final String PASSWORD = "shakti-pass-1";

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void anAgentWhoChoseAPasswordSignsInWithTheirEmail() {
        String email = signUp(PASSWORD).email();

        AuthDtos.AuthResponse session = authService.agentLogin(new AuthDtos.LoginRequest(email, PASSWORD));

        assertThat(session.token()).isNotBlank();
        assertThat(session.user().email()).isEqualTo(email);
        assertThat(session.user().role()).isEqualTo(Role.FIELD_AGENT);
    }

    @Test
    void theEmailIsNotCaseSensitive() {
        String email = signUp(PASSWORD).email();

        assertThat(authService.agentLogin(new AuthDtos.LoginRequest(email.toUpperCase(), PASSWORD))
                        .user()
                        .email())
                .isEqualTo(email);
    }

    @Test
    void theWrongPasswordIsRefused() {
        String email = signUp(PASSWORD).email();

        assertThatThrownBy(() -> authService.agentLogin(new AuthDtos.LoginRequest(email, "not-the-password")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid email or password");
    }

    /**
     * The point of keeping the password optional: signing up without one must
     * leave the account exactly as it was before, reachable by OTP.
     */
    @Test
    void anAgentWithoutAPasswordKeepsTheOtpRouteAndCannotSignInWithOne() {
        Signup signup = signUp(null);
        User agent = userRepository.findByEmailIgnoreCase(signup.email()).orElseThrow();
        assertThat(agent.getPasswordHash()).isNull();

        assertThatThrownBy(() -> authService.agentLogin(new AuthDtos.LoginRequest(signup.email(), PASSWORD)))
                .isInstanceOf(BusinessException.class)
                // The same answer as a wrong password: that no password is set is
                // not something an outsider should be able to find out.
                .hasMessageContaining("Invalid email or password");

        assertThat(authService
                        .verifyOtp(new AuthDtos.OtpVerifyRequest(signup.phone(), currentOtp(signup.phone())))
                        .user()
                        .email())
                .isEqualTo(signup.email());
    }

    /** A password is an addition, so the OTP must still work for agents who set one. */
    @Test
    void anAgentWithAPasswordCanStillUseTheOtp() {
        Signup signup = signUp(PASSWORD);

        assertThat(authService
                        .verifyOtp(new AuthDtos.OtpVerifyRequest(signup.phone(), currentOtp(signup.phone())))
                        .user()
                        .email())
                .isEqualTo(signup.email());
    }

    /** An agent's password is no way into the admin side. */
    @Test
    void anAgentCannotSignInAsAnAdmin() {
        String email = signUp(PASSWORD).email();

        assertThatThrownBy(() -> authService.adminLogin(new AuthDtos.LoginRequest(email, PASSWORD)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    void thePasswordIsStoredHashed() {
        String email = signUp(PASSWORD).email();

        String stored = userRepository.findByEmailIgnoreCase(email).orElseThrow().getPasswordHash();
        assertThat(stored).isNotNull().isNotEqualTo(PASSWORD).doesNotContain(PASSWORD);
    }

    private record Signup(String email, String phone) {}

    private Signup signUp(String password) {
        int n = SEQUENCE.incrementAndGet();
        String email = "agent.pass." + n + "." + System.nanoTime() + "@example.com";
        String phone = String.format("9%09d", 500_000_000 + n);
        authService.signup(new AuthDtos.SignupRequest("Agent " + n, email, phone, "Village", password));
        return new Signup(email.toLowerCase(), phone);
    }

    /** The code the service just issued; outside production it comes back in the response. */
    private String currentOtp(String phone) {
        String otp = authService.requestOtp(phone).devOtp();
        assertThat(otp).as("the test profile must expose the OTP").isNotNull();
        return otp;
    }
}
