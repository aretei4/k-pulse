package com.kahga.pluse.fieldagent;

import static org.assertj.core.api.Assertions.assertThat;

import com.kahga.pluse.auth.dto.AuthDtos;
import com.kahga.pluse.auth.service.AuthService;
import com.kahga.pluse.fieldagent.dto.FieldAgentDto;
import com.kahga.pluse.fieldagent.service.FieldAgentCsv;
import com.kahga.pluse.fieldagent.service.FieldAgentService;
import com.kahga.pluse.security.UserPrincipal;
import com.kahga.pluse.user.entity.Role;
import com.kahga.pluse.user.entity.User;
import com.kahga.pluse.user.repository.UserRepository;
import java.util.List;
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
 * What an agent gives at sign-up and what the admin sees of it afterwards: the
 * optional password, and the optional referral — who they say sent them.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AgentSignupDetailsTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FieldAgentCsv fieldAgentCsv;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private FieldAgentService fieldAgentService;

    @BeforeEach
    void signInAsSuperAdmin() {
        User admin = userRepository.findByRoleOrderByNameAsc(Role.SUPER_ADMIN).stream()
                .filter(User::isActive)
                .findFirst()
                .orElseThrow();
        var principal = UserPrincipal.from(admin);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void signingUpWithAPasswordAndAReferralKeepsBoth() {
        String phone = phone();

        authService.signup(new AuthDtos.SignupRequest(
                "Referred Agent", email(), phone, "Panasganda, Rajkanika", "9861000011", "Kansabansa7"));

        User agent = userRepository.findByPhone(phone).orElseThrow();
        assertThat(agent.getReferralNo()).isEqualTo("9861000011");
        assertThat(agent.getRole()).isEqualTo(Role.FIELD_AGENT);
        // Only the hash, never the password.
        assertThat(agent.getPasswordHash()).isNotNull().isNotEqualTo("Kansabansa7");
        assertThat(passwordEncoder.matches("Kansabansa7", agent.getPasswordHash())).isTrue();
    }

    /** Both are optional: an agent who gives neither signs in by OTP, as before. */
    @Test
    void signingUpWithNeitherLeavesNoPasswordAndNoReferral() {
        String phone = phone();

        authService.signup(new AuthDtos.SignupRequest("Plain Agent", email(), phone, "Erein", "  ", null));

        User agent = userRepository.findByPhone(phone).orElseThrow();
        assertThat(agent.getPasswordHash()).isNull();
        // Blank is nothing, not an empty string the admin's screen would show.
        assertThat(agent.getReferralNo()).isNull();
    }

    /** §7: the export carries the referral beside the rest of the sign-up details. */
    @Test
    void theExportCarriesTheReferralColumn() {
        String csv = fieldAgentCsv.build(List.of(new FieldAgentDto(
                UUID.randomUUID(),
                "A-00001",
                "Referred Agent",
                "9861000099",
                "referred@example.com",
                "Panasganda",
                "9861000011",
                true,
                null,
                null,
                null,
                null,
                null,
                com.kahga.pluse.fieldagent.dto.AgentAccessState.NONE,
                null,
                0,
                null,
                null,
                false,
                false,
                false,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null)));

        assertThat(csv.lines().toList().get(0)).contains("Address,Referral,District");
        assertThat(csv).contains("9861000011");
    }

    /** An admin correcting what the agent typed, from the Field agents panel. */
    @Test
    void anAdminCanSetCorrectAndClearTheReferral() {
        User agent = userRepository.findByRoleOrderByNameAsc(Role.FIELD_AGENT).stream()
                .findFirst()
                .orElseThrow();

        assertThat(fieldAgentService.updateReferral(agent.getId(), "98610-00099").referralNo())
                .as("cleaned as it is stored, so the panel shows one shape")
                .isEqualTo("9861000099");
        assertThat(fieldAgentService.updateReferral(agent.getId(), "9861000088").referralNo())
                .isEqualTo("9861000088");
        assertThat(fieldAgentService.updateReferral(agent.getId(), "   ").referralNo())
                .as("blank is how a wrong number recorded at sign-up is undone")
                .isNull();
    }

    @Test
    void anAdminIsHeldToTheSameRuleAsTheSignUpForm() {
        User agent = userRepository.findByRoleOrderByNameAsc(Role.FIELD_AGENT).stream()
                .findFirst()
                .orElseThrow();

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> fieldAgentService.updateReferral(agent.getId(), "ward 7"))
                .isInstanceOf(com.kahga.pluse.common.exception.BusinessException.class)
                .hasMessageContaining("10-digit mobile number");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> fieldAgentService.updateReferral(agent.getId(), agent.getPhone()))
                .isInstanceOf(com.kahga.pluse.common.exception.BusinessException.class)
                .hasMessageContaining("own referral");
    }

    private String phone() {
        return "9" + String.format("%09d", Math.abs(UUID.randomUUID().hashCode()) % 1_000_000_000);
    }

    private String email() {
        return "agent-" + UUID.randomUUID() + "@example.com";
    }
}
