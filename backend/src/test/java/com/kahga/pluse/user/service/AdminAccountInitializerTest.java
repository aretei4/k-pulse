package com.kahga.pluse.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kahga.pluse.config.KPulseProperties;
import com.kahga.pluse.user.entity.Role;
import com.kahga.pluse.user.entity.User;
import com.kahga.pluse.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** The admin login comes from kpulse.admin.* and has to follow it across restarts. */
class AdminAccountInitializerTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final UserRepository users = mock(UserRepository.class);
    private final KPulseProperties properties = new KPulseProperties();
    private AdminAccountInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new AdminAccountInitializer(properties, users, encoder);
    }

    @Test
    void createsTheAdminWhenNoAccountHasThatEmail() {
        configure(" Admin@K-Pulse.in ", "Admin@123");
        when(users.findByEmailIgnoreCase("admin@k-pulse.in")).thenReturn(Optional.empty());

        initializer.run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@k-pulse.in");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.SUPER_ADMIN);
        assertThat(saved.getValue().isActive()).isTrue();
        assertThat(encoder.matches("Admin@123", saved.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void resetsThePasswordOfAnExistingAdminWhenThePropertiesChanged() {
        configure("admin@k-pulse.in", "Second@123");
        User existing = admin("First@123");
        when(users.findByEmailIgnoreCase("admin@k-pulse.in")).thenReturn(Optional.of(existing));

        initializer.run(null);

        verify(users).save(existing);
        assertThat(encoder.matches("Second@123", existing.getPasswordHash())).isTrue();
        assertThat(encoder.matches("First@123", existing.getPasswordHash())).isFalse();
    }

    @Test
    void leavesAMatchingPasswordAlone() {
        configure("admin@k-pulse.in", "Same@1234");
        User existing = admin("Same@1234");
        when(users.findByEmailIgnoreCase("admin@k-pulse.in")).thenReturn(Optional.of(existing));

        initializer.run(null);

        verify(users, never()).save(any());
    }

    @Test
    void refusesToTurnAFieldAgentIntoAnAdmin() {
        configure("prakash@example.com", "Admin@123");
        User agent = admin("irrelevant");
        agent.setRole(Role.FIELD_AGENT);
        when(users.findByEmailIgnoreCase("prakash@example.com")).thenReturn(Optional.of(agent));

        assertThatThrownBy(() -> initializer.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("field agent");
        verify(users, never()).save(any());
    }

    @Test
    void failsWhenOnlyOneOfEmailAndPasswordIsSet() {
        configure("admin@k-pulse.in", "");

        assertThatThrownBy(() -> initializer.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("kpulse.admin.password");
    }

    @Test
    void rejectsAPasswordShorterThanEightCharacters() {
        configure("admin@k-pulse.in", "short");

        assertThatThrownBy(() -> initializer.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 8");
    }

    @Test
    void doesNothingWhenBothAreBlank() {
        configure("", "");
        when(users.findByRoleOrderByNameAsc(Role.SUPER_ADMIN)).thenReturn(List.of());

        initializer.run(null);

        verify(users, never()).save(any());
    }

    private void configure(String email, String password) {
        properties.getAdmin().setEmail(email);
        properties.getAdmin().setPassword(password);
    }

    private User admin(String password) {
        return User.builder()
                .id(UUID.randomUUID())
                .name("Constituency Admin")
                .email("admin@k-pulse.in")
                .passwordHash(encoder.encode(password))
                .role(Role.SUPER_ADMIN)
                .active(true)
                .createdAt(Instant.now())
                .build();
    }
}
