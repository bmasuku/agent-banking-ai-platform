package com.agentbanking.platform.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI platformOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Agent Banking & Float Management Platform API")
                        .version("1.0.0")
                        .description("""
                                Simulated digital agent banking platform. All money movements are simulated and
                                recorded in an immutable double-entry ledger. Authenticate with POST /api/v1/auth/login
                                and use the returned access token as a Bearer token. Financial POST endpoints require an
                                Idempotency-Key header.""")
                        .contact(new Contact().name("Platform Engineering")))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    @Bean
    public GroupedOpenApi allApis() {
        return GroupedOpenApi.builder().group("all").pathsToMatch("/api/**").build();
    }

    @Bean
    public GroupedOpenApi mobileApis() {
        return GroupedOpenApi.builder().group("mobile-agent-area-manager")
                .pathsToMatch("/api/v1/auth/**", "/api/v1/agent/**", "/api/v1/area-manager/**",
                        "/api/v1/notifications/**", "/api/v1/credit-scores/**", "/api/v1/predictions/**")
                .build();
    }

    @Bean
    public GroupedOpenApi webApis() {
        return GroupedOpenApi.builder().group("web-risk-finance-admin")
                .pathsToMatch("/api/v1/auth/**", "/api/v1/risk/**", "/api/v1/finance/**", "/api/v1/admin/**",
                        "/api/v1/agents/**", "/api/v1/areas/**", "/api/v1/transactions/**", "/api/v1/audit-logs/**",
                        "/api/v1/notifications/**", "/api/v1/credit-scores/**", "/api/v1/predictions/**")
                .build();
    }
}
