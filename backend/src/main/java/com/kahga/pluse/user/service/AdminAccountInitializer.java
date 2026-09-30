package com.kahga.pluse.user.service;

import com.kahga.pluse.config.KPulseProperties;
import com.kahga.pluse.user.entity.Role;
import com.kahga.pluse.user.entity.User;
import com.kahga.pluse.user.repository.UserRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the admin login from {@code kpulse.admin.email} and
 * {@code kpulse.admin.password}, in every profile, on every start.
 *
 * <ul>
 *   <li>No account with that email: an admin is created.
 *   <li>An admin with that email: its password is reset to the configured value
 *       if they differ. Changing the password in the properties file and
 *       restarting is how the login is changed.
 *   <li>Both properties blank: existing admins are left untouched.
 * </ul>
 *
 * <p>Runs before {@link com.kahga.pluse.seed.DataSeeder}, which no longer creates an
 * admin. The admin login and the demo data are independent, so prod can have an
 * admin without loading fake voters.
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class AdminAccountInitializer implements ApplicationRunner {

    static final int MIN_PASSWORD_LENGTH = 8;

    private final KPulseProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        KPulseProperties.Admin config = properties.getAdmin();
        String email = config.getEmail() == null ? "" : config.getEmail().trim().toLowerCase(Locale.ROOT);
        String password = config.getPassword() == null ? "" : config.getPassword();

        if (email.isEmpty() && password.isEmpty()) {
            if (userRepository.findByRoleOrderByNameAsc(Role.SUPER_ADMIN).isEmpty()) {
                log.warn("No admin account exists. Set kpulse.admin.email and kpulse.admin.password to create one.");
            }
            return;
        }
        if (email.isEmpty() || password.isEmpty()) {
            throw new IllegalStateException(
                    "Set both kpulse.admin.email and kpulse.admin.password, or leave both blank");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "kpulse.admin.password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }

        Optional<User> existing = userRepository.findByEmailIgnoreCase(email);
        if (existing.isEmpty()) {
            userRepository.save(User.builder()
                    .id(UUID.randomUUID())
                    .name(config.getName())
                    .email(email)
                    .passwordHash(passwordEncoder.encode(password))
                    .role(Role.SUPER_ADMIN)
                    .active(true)
                    .createdAt(Instant.now())
                    .build());
            log.info("Created admin account {} from kpulse.admin.email", email);
            return;
        }

        User account = existing.get();
        if (!account.getRole().isAdminKind()) {
            // Never promote silently: a typo in the email would turn a field agent into an admin.
            throw new IllegalStateException("kpulse.admin.email " + email
                    + " belongs to a field agent. Use an email that is not already registered.");
        }
        if (account.getPasswordHash() == null || !passwordEncoder.matches(password, account.getPasswordHash())) {
            account.setPasswordHash(passwordEncoder.encode(password));
            userRepository.save(account);
            log.info("Admin account {}: password reset to the value in kpulse.admin.password", email);
        } else {
            log.info("Admin account {}: password already matches kpulse.admin.password", email);
        }
        if (!account.isActive()) {
            log.warn("Admin account {} is deactivated, so it cannot sign in until it is reactivated", email);
        }
    }
}
