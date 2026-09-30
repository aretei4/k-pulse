package com.kahga.pluse.config;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "kpulse")
public class KPulseProperties {

    private Jwt jwt = new Jwt();
    private Access access = new Access();
    private Otp otp = new Otp();
    private Cors cors = new Cors();
    private Seed seed = new Seed();
    private Admin admin = new Admin();

    @Getter
    @Setter
    public static class Jwt {
        private String secret;
        private int expiryHours = 12;
    }

    @Getter
    @Setter
    public static class Access {
        /** FR: 6 months unless the admin picks another duration. */
        private int defaultExpiryMonths = 6;
    }

    @Getter
    @Setter
    public static class Otp {
        /** Off unless a profile turns it on: an exposed OTP lets anyone sign in as any agent. */
        private boolean exposeInResponse = false;
        private int ttlMinutes = 10;
    }

    @Getter
    @Setter
    public static class Cors {
        private List<String> allowedOrigins = List.of("http://localhost:5173");
    }

    @Getter
    @Setter
    public static class Seed {
        /** Off unless a profile turns it on: the demo dataset is fake voters. */
        private boolean enabled = false;
    }
    /**
     * The admin login. On every start the account is created if missing, and its
     * password reset to this value if they differ. Leave both blank to leave
     * existing admins untouched. See AdminAccountInitializer.
     */
    @Getter
    @Setter
    public static class Admin {
        private String email = "";
        private String password = "";
        private String name = "Constituency Admin";
    }
}
