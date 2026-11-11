package com.kahga.pluse.user.service;

import com.kahga.pluse.common.exception.BusinessException;
import com.kahga.pluse.user.dto.AgentPasswordStatusDto;
import com.kahga.pluse.user.dto.ChangePasswordRequest;
import com.kahga.pluse.user.entity.User;
import com.kahga.pluse.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FA-24 and FA-25: the agent asks for a new password from inside the app, the
 * admin sets one from the Field agents page, and the agent then chooses their
 * own.
 *
 * <p>Both halves live here so the one rule that matters is written once: a
 * password an admin set is temporary, and the agent must replace it before
 * doing anything else.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentPasswordService {

    /**
     * The admin may choose an easy temporary password because it lives for one
     * sign-in (FA-19). What the agent then chooses is held to the longer rule.
     */
    public static final int MIN_TEMPORARY_LENGTH = 6;

    public static final int MIN_CHOSEN_LENGTH = 8;

    /**
     * How long a temporary password stays usable (FA-N5). After that the agent
     * asks again rather than an old scrap of paper still opening the account.
     */
    public static final int TEMPORARY_VALID_DAYS = 7;

    /**
     * Words an agent can be told over the phone and type without help. Short,
     * unambiguous, and nothing that reads as a slur or a name.
     */
    private static final List<String> WORDS =
            List.of("seva", "jaga", "sathi", "bela", "tara", "nila", "pala", "mela", "sita", "bana");

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /** "seva4821" — easy to say down a phone line, and it lives for one sign-in. */
    public static String suggest() {
        return WORDS.get(RANDOM.nextInt(WORDS.size())) + (1000 + RANDOM.nextInt(9000));
    }

    public AgentPasswordStatusDto status(User agent) {
        return new AgentPasswordStatusDto(
                agent.getPasswordHash() != null,
                agent.isMustChangePassword(),
                agent.getPasswordResetRequestedAt());
    }

    /**
     * FA-24. The agent asks; the admin is the one who acts. Asking twice does
     * not queue a second ask, it just leaves the first one standing, so the
     * admin's list cannot fill up with the same person.
     */
    @Transactional
    public AgentPasswordStatusDto request(User agent) {
        if (agent.getPasswordResetRequestedAt() == null) {
            agent.setPasswordResetRequestedAt(Instant.now());
            userRepository.save(agent);
            log.info("Agent {} asked for a password reset", agent.getId());
        }
        return status(agent);
    }

    /** The agent changed their mind, or an admin saw them in person. */
    @Transactional
    public AgentPasswordStatusDto withdraw(User agent) {
        if (agent.getPasswordResetRequestedAt() != null) {
            agent.setPasswordResetRequestedAt(null);
            userRepository.save(agent);
        }
        return status(agent);
    }

    /**
     * The agent choosing their own password — after a reset, or simply because
     * they want to.
     *
     * <p>The current one is required when they have one. An agent who signs in
     * by OTP and has never set a password may set a first one without it: they
     * proved who they are with the code that got them in here.
     */
    @Transactional
    public AgentPasswordStatusDto change(User agent, ChangePasswordRequest request) {
        if (agent.getPasswordHash() != null) {
            if (request.currentPassword() == null
                    || !passwordEncoder.matches(request.currentPassword(), agent.getPasswordHash())) {
                throw new BusinessException("That is not your current password");
            }
            if (passwordEncoder.matches(request.newPassword(), agent.getPasswordHash())) {
                throw new BusinessException("Choose a password you have not used before");
            }
        }
        requireChosenPasswordRule(request.newPassword());

        agent.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        // Chosen by the agent, so nothing temporary is left standing: no forced
        // change, and no ask for the admin to answer.
        agent.setMustChangePassword(false);
        agent.setPasswordResetRequestedAt(null);
        agent.setLoginUpdatedBy(agent.getId());
        agent.setLoginUpdatedAt(Instant.now());
        userRepository.save(agent);
        return status(agent);
    }

    /**
     * FA-19/FA-25, the admin's side. Returns the password in plain text exactly
     * once, for the admin to read out; only its hash is kept.
     *
     * @param chosen what the admin typed, or null to use a suggestion
     */
    @Transactional
    public String reset(User agent, String chosen, UUID byAdminId) {
        String password = chosen == null || chosen.isBlank() ? suggest() : chosen.trim();
        requireTemporaryPasswordRule(password);

        agent.setPasswordHash(passwordEncoder.encode(password));
        agent.setMustChangePassword(true);
        agent.setPasswordResetRequestedAt(null);
        agent.setLoginUpdatedBy(byAdminId);
        agent.setLoginUpdatedAt(Instant.now());
        userRepository.save(agent);
        log.info("Admin {} reset the password of agent {}", byAdminId, agent.getId());
        return password;
    }

    /** The admin has dealt with the ask another way, or it was a mistake. */
    @Transactional
    public void dismissRequest(User agent) {
        agent.setPasswordResetRequestedAt(null);
        userRepository.save(agent);
    }

    /**
     * Whether a temporary password has gone stale (FA-N5). Checked at sign-in:
     * an agent who never used the one they were given asks for another rather
     * than it working for ever.
     */
    public boolean temporaryPasswordExpired(User agent) {
        return agent.isMustChangePassword()
                && agent.getLoginUpdatedAt() != null
                && agent.getLoginUpdatedAt()
                        .plusSeconds(TEMPORARY_VALID_DAYS * 24L * 3600L)
                        .isBefore(Instant.now());
    }

    private void requireTemporaryPasswordRule(String password) {
        if (password.length() < MIN_TEMPORARY_LENGTH || password.chars().anyMatch(Character::isWhitespace)) {
            throw new BusinessException(
                    "A temporary password needs at least " + MIN_TEMPORARY_LENGTH + " characters and no spaces");
        }
    }

    private void requireChosenPasswordRule(String password) {
        boolean longEnough = password != null && password.length() >= MIN_CHOSEN_LENGTH;
        boolean mixed = password != null
                && password.chars().anyMatch(Character::isLetter)
                && password.chars().anyMatch(Character::isDigit);
        if (!longEnough || !mixed || password.chars().anyMatch(Character::isWhitespace)) {
            throw new BusinessException("Choose at least " + MIN_CHOSEN_LENGTH
                    + " characters with a letter and a number, and no spaces");
        }
    }
}
