package com.agentbanking.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

/** Authentication is JWT-only (see {@code JwtAuthenticationFilter}); the default in-memory user is disabled. */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableAsync
public class AgentBankingApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentBankingApplication.class, args);
    }
}
