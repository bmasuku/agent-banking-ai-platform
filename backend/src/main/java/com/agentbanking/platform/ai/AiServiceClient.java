package com.agentbanking.platform.ai;

import com.agentbanking.platform.admin.SettingKey;
import com.agentbanking.platform.admin.SystemSettingService;
import com.agentbanking.platform.common.exception.BusinessException;
import com.agentbanking.platform.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * HTTP client for the Python AI intelligence service (snake_case JSON). Soft calls return
 * {@link Optional#empty()} so callers can fall back to explainable rules. Hard calls throw
 * {@code AI_SERVICE_UNAVAILABLE} when the service is required (anomaly, optimisation, assistant).
 */
@Slf4j
@Component
public class AiServiceClient {

    public static final String SOURCE_AI = "AI_SERVICE";
    public static final String SOURCE_RULES = "RULES_FALLBACK";

    private static final ThreadLocal<Boolean> RULES_ONLY = new ThreadLocal<>();

    private final RestClient restClient;
    private final RestClient assistantClient;
    private final AppProperties.Ai config;
    private final SystemSettingService settings;
    private volatile Instant unavailableUntil = Instant.EPOCH;

    public AiServiceClient(RestClient.Builder builder, ObjectMapper objectMapper, AppProperties properties,
                           SystemSettingService settings) {
        this.config = properties.getAi();
        this.settings = settings;
        ObjectMapper snakeCase = objectMapper.copy().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        this.restClient = buildClient(builder, snakeCase, config.getBaseUrl(), config.getConnectTimeout(), config.getReadTimeout());
        this.assistantClient = buildClient(builder, snakeCase, config.getBaseUrl(), config.getConnectTimeout(),
                config.getAssistantReadTimeout());
    }

    private static RestClient buildClient(RestClient.Builder builder, ObjectMapper snakeCase, String baseUrl,
                                          Duration connect, Duration read) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) connect.toMillis());
        factory.setReadTimeout((int) read.toMillis());
        return builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .messageConverters(converters -> {
                    converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
                    converters.add(0, new MappingJackson2HttpMessageConverter(snakeCase));
                })
                .build();
    }

    /** Runs {@code action} with AI calls disabled on this thread, so demo seeding is deterministic and fast. */
    public static <T> T withRulesOnly(Supplier<T> action) {
        Boolean previous = RULES_ONLY.get();
        RULES_ONLY.set(Boolean.TRUE);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                RULES_ONLY.remove();
            } else {
                RULES_ONLY.set(previous);
            }
        }
    }

    public boolean isEnabled() {
        return !Boolean.TRUE.equals(RULES_ONLY.get()) && config.isEnabled() && settings.getBoolean(SettingKey.AI_SERVICE_ENABLED);
    }

    public <T> Optional<T> post(String path, Object body, Class<T> responseType) {
        if (!isEnabled() || Instant.now().isBefore(unavailableUntil)) {
            return Optional.empty();
        }
        try {
            T response = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Internal-Api-Key", config.getApiKey())
                    .body(body)
                    .retrieve()
                    .body(responseType);
            return Optional.ofNullable(response);
        } catch (Exception ex) {
            unavailableUntil = Instant.now().plus(config.getRetryAfterFailure());
            log.warn("AI service call {} failed ({}); using rule-based fallback for {}s", path, ex.getMessage(),
                    config.getRetryAfterFailure().toSeconds());
            return Optional.empty();
        }
    }

    /**
     * Required AI call (anomaly / optimisation). Throws when the service is disabled or unreachable
     * instead of silently falling back.
     */
    public <T> T postRequired(String path, Object body, Class<T> responseType) {
        return exchangeRequired(restClient, path, body, responseType);
    }

    /** Assistant chat uses a longer read timeout. */
    public <T> T postAssistant(String path, Object body, Class<T> responseType) {
        return exchangeRequired(assistantClient, path, body, responseType);
    }

    /** Required GET (governance proxy). Throws when the service is disabled or unreachable. */
    public <T> T getRequired(String path, Class<T> responseType) {
        return getRequired(path, Map.of(), responseType);
    }

    public <T> T getRequired(String path, Map<String, ?> queryParams, Class<T> responseType) {
        if (!isEnabled()) {
            throw unavailable("The AI intelligence service is disabled");
        }
        if (Instant.now().isBefore(unavailableUntil)) {
            throw unavailable("The AI intelligence service is temporarily unavailable");
        }
        try {
            T response = restClient.get()
                    .uri(uriBuilder -> {
                        var b = uriBuilder.path(path);
                        queryParams.forEach((k, v) -> {
                            if (v != null) {
                                b.queryParam(k, v);
                            }
                        });
                        return b.build();
                    })
                    .header("X-Internal-Api-Key", config.getApiKey())
                    .retrieve()
                    .body(responseType);
            if (response == null) {
                throw unavailable("The AI intelligence service returned an empty response");
            }
            return response;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            unavailableUntil = Instant.now().plus(config.getRetryAfterFailure());
            log.warn("Required AI service GET {} failed: {}", path, ex.getMessage());
            throw unavailable("The AI intelligence service is unavailable: " + ex.getMessage());
        }
    }

    private <T> T exchangeRequired(RestClient client, String path, Object body, Class<T> responseType) {
        if (!isEnabled()) {
            throw unavailable("The AI intelligence service is disabled");
        }
        if (Instant.now().isBefore(unavailableUntil)) {
            throw unavailable("The AI intelligence service is temporarily unavailable");
        }
        try {
            T response = client.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Internal-Api-Key", config.getApiKey())
                    .body(body)
                    .retrieve()
                    .body(responseType);
            if (response == null) {
                throw unavailable("The AI intelligence service returned an empty response");
            }
            return response;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            unavailableUntil = Instant.now().plus(config.getRetryAfterFailure());
            log.warn("Required AI service call {} failed: {}", path, ex.getMessage());
            throw unavailable("The AI intelligence service is unavailable: " + ex.getMessage());
        }
    }

    private static BusinessException unavailable(String message) {
        return BusinessException.serviceUnavailable("AI_SERVICE_UNAVAILABLE", message);
    }

    public Status status() {
        boolean reachable = false;
        Map<?, ?> health = null;
        if (config.isEnabled()) {
            try {
                health = restClient.get().uri("/health").retrieve().body(Map.class);
                reachable = true;
            } catch (Exception ex) {
                log.debug("AI service health probe failed: {}", ex.getMessage());
            }
        }
        return new Status(config.isEnabled(), settings.getBoolean(SettingKey.AI_SERVICE_ENABLED), config.getBaseUrl(), reachable,
                health, Instant.now().isBefore(unavailableUntil) ? unavailableUntil : null,
                isEnabled() && reachable ? SOURCE_AI : SOURCE_RULES);
    }

    public record Status(boolean configEnabled, boolean settingEnabled, String baseUrl, boolean reachable, Map<?, ?> health,
                         Instant coolingDownUntil, String activeSource) {
    }
}
