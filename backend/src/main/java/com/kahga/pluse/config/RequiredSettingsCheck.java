package com.kahga.pluse.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Refuses to start when a required setting is missing, and says which one.
 *
 * <p>The uat and prod profiles deliberately give secrets no default, e.g.
 * {@code kpulse.jwt.secret=${KPULSE_JWT_SECRET}}. But {@code @ConfigurationProperties}
 * binding does not fail on an unresolved placeholder — it binds the literal text
 * {@code "${KPULSE_JWT_SECRET}"}. Without this check a missing secret surfaces
 * later as a misleading "must be at least 32 bytes" error, and a missing database
 * password as a PostgreSQL login attempt using the placeholder text itself.
 * Resolving these keys strictly here turns every such gap into one clear message
 * before any bean is created.
 *
 * <p>Runs last among the environment post-processors, so config files, the active
 * profile and any imported secrets file are already in place.
 */
public class RequiredSettingsCheck implements EnvironmentPostProcessor, Ordered {

    static final List<String> REQUIRED = List.of(
            "spring.datasource.url",
            "spring.datasource.username",
            "spring.datasource.password",
            "kpulse.jwt.secret");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        List<String> problems = new ArrayList<>();
        for (String key : REQUIRED) {
            try {
                // Strict resolution: throws on an absent key or an unresolved ${...},
                // where binding would have quietly used the literal.
                environment.getRequiredProperty(key);
            } catch (IllegalStateException | IllegalArgumentException ex) {
                problems.add(key + " - " + ex.getMessage());
            }
        }
        if (problems.isEmpty()) {
            return;
        }
        throw new IllegalStateException("K-Pulse cannot start: required settings are missing for profile "
                + Arrays.toString(environment.getActiveProfiles())
                + ". Set the environment variable named in each placeholder, or add the key to the"
                + " secrets file (see backend/config/kpulse-secrets.properties.example):\n  "
                + String.join("\n  ", problems));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
