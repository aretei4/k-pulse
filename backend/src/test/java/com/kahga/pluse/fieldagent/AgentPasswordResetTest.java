package com.kahga.pluse.fieldagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.kahga.pluse.auth.dto.AuthDtos;
import com.kahga.pluse.auth.service.AuthService;
import com.kahga.pluse.common.exception.BusinessException;
import com.kahga.pluse.fieldagent.dto.FieldAgentDto;
import com.kahga.pluse.fieldagent.dto.FieldAgentFilter;
import com.kahga.pluse.fieldagent.dto.FieldAgentSort;
import com.kahga.pluse.fieldagent.dto.ResetPasswordResult;
import com.kahga.pluse.fieldagent.service.FieldAgentService;
import com.kahga.pluse.security.UserPrincipal;
import com.kahga.pluse.user.dto.AgentPasswordStatusDto;
import com.kahga.pluse.user.dto.ChangePasswordRequest;
import com.kahga.pluse.user.entity.Role;
import com.kahga.pluse.user.entity.User;
import com.kahga.pluse.user.repository.UserRepository;
import com.kahga.pluse.user.service.AgentPasswordService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * FA-24 and FA-25, end to end: the agent asks from inside the app, the admin
 * resets from the Field agents page, the agent signs in once with what they
 * were given and has to choose their own.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AgentPasswordResetTest {

    @Autowired
    private AgentPasswordService agentPasswordService;

    @Autowired
    private FieldAgentService fieldAgentService;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void signInAsSuperAdmin() {
        signIn(userRepository.findByRoleOrderByNameAsc(Role.SUPER_ADMIN).stream()
                .filter(User::isActive)
                .findFirst()
                .orElseThrow());
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theAgentsAskShowsUpOnTheAdminsListAndTheResetAnswersIt() {
        User agent = agent();

        agentPasswordService.request(agent);
        assertThat(row(agent.getId()).passwordResetRequestedAt())
                .as("the admin's list marks the agent as waiting")
                .isNotNull();

        ResetPasswordResult result = fieldAgentService.resetPassword(agent.getId(), null);

        assertThat(result.temporaryPassword()).matches("[a-z]+\\d{4}");
        assertThat(result.validDays()).isEqualTo(AgentPasswordService.TEMPORARY_VALID_DAYS);
        FieldAgentDto after = row(agent.getId());
        assertThat(after.passwordResetRequestedAt()).as("the ask is answered").isNull();
        assertThat(after.passwordSet()).isTrue();
        assertThat(after.mustChangePassword()).isTrue();
    }

    /** Asking twice is the same ask: the admin's list must not fill up with one agent. */
    @Test
    void askingTwiceLeavesOneAsk() {
        User agent = agent();

        AgentPasswordStatusDto first = agentPasswordService.request(agent);
        AgentPasswordStatusDto second = agentPasswordService.request(reload(agent));

        // To the millisecond: the second read comes back from the database, which
        // keeps microseconds where the in-memory value kept nanoseconds. What is
        // being asserted is that it is the same ask, not a fresh one.
        assertThat(second.resetRequestedAt()).isCloseTo(first.resetRequestedAt(), within(1, ChronoUnit.MILLIS));
    }

    @Test
    void theAdminMayTypeTheirOwnEasyPasswordButNotAWeakOne() {
        User agent = agent();

        ResetPasswordResult chosen = fieldAgentService.resetPassword(agent.getId(), "seva4821");
        assertThat(chosen.temporaryPassword()).isEqualTo("seva4821");
        assertThat(passwordEncoder.matches("seva4821", reload(agent).getPasswordHash())).isTrue();

        assertThatThrownBy(() -> fieldAgentService.resetPassword(agent.getId(), "sev1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("at least 6 characters");
        assertThatThrownBy(() -> fieldAgentService.resetPassword(agent.getId(), "seva 4821"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no spaces");
    }

    /** FA-25: the agent signs in with it, and the response says to change it. */
    @Test
    void theTemporaryPasswordSignsTheAgentInAndAsksThemToChangeIt() {
        User agent = agent();
        String temporary = fieldAgentService.resetPassword(agent.getId(), null).temporaryPassword();

        AuthDtos.AuthResponse response =
                authService.agentLogin(new AuthDtos.LoginRequest(agent.getEmail(), temporary));

        assertThat(response.user().mustChangePassword()).isTrue();
    }

    /** FA-N5: one that was never used stops working rather than lasting for ever. */
    @Test
    void aTemporaryPasswordThatWasNeverUsedExpires() {
        User agent = agent();
        String temporary = fieldAgentService.resetPassword(agent.getId(), null).temporaryPassword();

        User stale = reload(agent);
        stale.setLoginUpdatedAt(Instant.now().minus(AgentPasswordService.TEMPORARY_VALID_DAYS + 1, ChronoUnit.DAYS));
        userRepository.save(stale);

        assertThatThrownBy(() -> authService.agentLogin(new AuthDtos.LoginRequest(agent.getEmail(), temporary)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void choosingTheirOwnPasswordEndsTheForcedChange() {
        User agent = agent();
        String temporary = fieldAgentService.resetPassword(agent.getId(), null).temporaryPassword();

        AgentPasswordStatusDto status =
                agentPasswordService.change(reload(agent), new ChangePasswordRequest(temporary, "Kansabansa7"));

        assertThat(status.mustChangePassword()).isFalse();
        assertThat(status.passwordSet()).isTrue();
        assertThat(authService
                        .agentLogin(new AuthDtos.LoginRequest(agent.getEmail(), "Kansabansa7"))
                        .user()
                        .mustChangePassword())
                .isFalse();
    }

    @Test
    void theAgentCannotChangeItWithoutTheCurrentOneOrToSomethingTooWeak() {
        User agent = agent();
        fieldAgentService.resetPassword(agent.getId(), "seva4821");

        assertThatThrownBy(() ->
                        agentPasswordService.change(reload(agent), new ChangePasswordRequest("wrong", "Kansabansa7")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("current password");

        assertThatThrownBy(() ->
                        agentPasswordService.change(reload(agent), new ChangePasswordRequest("seva4821", "short1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("at least 8 characters");

        // The temporary one cannot simply be kept.
        assertThatThrownBy(() ->
                        agentPasswordService.change(reload(agent), new ChangePasswordRequest("seva4821", "seva4821")))
                .isInstanceOf(BusinessException.class);
    }

    /** An agent who signs in by OTP and has never set one may set a first password. */
    @Test
    void anAgentWithNoPasswordMaySetOneWithoutGivingACurrentOne() {
        User agent = agent();
        agent.setPasswordHash(null);
        userRepository.save(agent);

        AgentPasswordStatusDto status =
                agentPasswordService.change(reload(agent), new ChangePasswordRequest(null, "Panasganda4"));

        assertThat(status.passwordSet()).isTrue();
        assertThat(status.mustChangePassword()).isFalse();
    }

    @Test
    void dismissingTheAskSetsNoPassword() {
        User agent = agent();
        agentPasswordService.request(agent);
        String before = reload(agent).getPasswordHash();

        FieldAgentDto after = fieldAgentService.dismissPasswordRequest(agent.getId());

        assertThat(after.passwordResetRequestedAt()).isNull();
        assertThat(reload(agent).getPasswordHash()).isEqualTo(before);
    }

    private FieldAgentDto row(UUID agentId) {
        return fieldAgentService.page(new FieldAgentFilter(fieldAgentService.areaUnitIds(null, null, null),
                        null,
                        null, null,
                        null,
                        FieldAgentSort.NAME,
                        false,
                        0,
                        100))
                .items()
                .stream()
                .filter(item -> item.id().equals(agentId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the agent is not on the admin's list"));
    }

    /** A seeded agent who holds a grant, so the admin's scope check can see them. */
    private User agent() {
        return userRepository.findByRoleOrderByNameAsc(Role.FIELD_AGENT).stream()
                .filter(user -> user.getEmail() != null)
                .filter(user -> row(user.getId()) != null)
                .findFirst()
                .orElseThrow();
    }

    private User reload(User agent) {
        return userRepository.findById(agent.getId()).orElseThrow();
    }

    private void signIn(User user) {
        var principal = UserPrincipal.from(user);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
