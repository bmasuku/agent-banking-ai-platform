package com.agentbanking.platform.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String timezone = "Africa/Harare";
    private String currency = "USD";
    private Security security = new Security();
    private Cors cors = new Cors();
    private Ai ai = new Ai();
    private Demo demo = new Demo();
    private Bootstrap bootstrap = new Bootstrap();
    private Scheduling scheduling = new Scheduling();

    @Getter
    @Setter
    public static class Security {
        private Jwt jwt = new Jwt();
        private int maxFailedLogins = 5;
    }

    @Getter
    @Setter
    public static class Jwt {
        private String secret;
        private String issuer = "agent-banking-platform";
        private Duration accessTokenTtl = Duration.ofMinutes(15);
        private Duration refreshTokenTtl = Duration.ofDays(7);
    }

    @Getter
    @Setter
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class Ai {
        private boolean enabled = true;
        private String baseUrl = "http://localhost:8000";
        private String apiKey = "local-dev-internal-key-change-me";
        private Duration connectTimeout = Duration.ofSeconds(2);
        private Duration readTimeout = Duration.ofSeconds(5);
        private Duration assistantReadTimeout = Duration.ofSeconds(45);
        private Duration retryAfterFailure = Duration.ofSeconds(30);
    }

    @Getter
    @Setter
    public static class Demo {
        private boolean seedOnStartup = true;
        private String defaultPassword = "Demo@2026!";
        private int historyDays = 14;
        private long randomSeed = 20261125L;
    }

    @Getter
    @Setter
    public static class Bootstrap {
        private String adminUsername = "admin";
        private String adminEmail = "admin@agentbanking.local";
        private String adminPassword = "Demo@2026!";
    }

    @Getter
    @Setter
    public static class Scheduling {
        private boolean enabled = true;
        private String penaltyAccrualCron = "0 30 0 * * *";
    }
}
