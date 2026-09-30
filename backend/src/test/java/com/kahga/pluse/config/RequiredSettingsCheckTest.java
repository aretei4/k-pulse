package com.kahga.pluse.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * uat and prod must refuse to start without their secrets. Plain property binding
 * would silently use a placeholder's literal text, so this pins the check that
 * stops that from happening.
 */
class RequiredSettingsCheckTest {

    private final RequiredSettingsCheck check = new RequiredSettingsCheck();

    @Test
    void passesWhenEverySettingResolves() {
        assertThatCode(() -> run(complete())).doesNotThrowAnyException();
    }

    @Test
    void anEmptyPasswordCountsAsSet() {
        // dev uses ${KPULSE_DB_PASSWORD:} for H2, which resolves to "".
        Map<String, Object> settings = complete();
        settings.put("spring.datasource.password", "");
        assertThatCode(() -> run(settings)).doesNotThrowAnyException();
    }

    @Test
    void namesEveryUnresolvedPlaceholderRatherThanBindingItsLiteral() {
        Map<String, Object> settings = complete();
        settings.put("kpulse.jwt.secret", "${KPULSE_TEST_UNSET_JWT_SECRET}");
        settings.put("spring.datasource.password", "${KPULSE_TEST_UNSET_DB_PASSWORD}");

        assertThatThrownBy(() -> run(settings))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("kpulse.jwt.secret")
                .hasMessageContaining("KPULSE_TEST_UNSET_JWT_SECRET")
                .hasMessageContaining("spring.datasource.password")
                .hasMessageContaining("KPULSE_TEST_UNSET_DB_PASSWORD");
    }

    @Test
    void reportsAKeyThatIsAbsentAltogether() {
        Map<String, Object> settings = complete();
        settings.remove("kpulse.jwt.secret");

        assertThatThrownBy(() -> run(settings))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("kpulse.jwt.secret");
    }

    private void run(Map<String, Object> settings) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("settings", settings));
        check.postProcessEnvironment(environment, new SpringApplication());
    }

    private static Map<String, Object> complete() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("spring.datasource.url", "jdbc:h2:mem:check");
        settings.put("spring.datasource.username", "sa");
        settings.put("spring.datasource.password", "secret");
        settings.put("kpulse.jwt.secret", "a-secret-that-is-comfortably-over-thirty-two-bytes");
        return settings;
    }
}
