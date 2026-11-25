package com.agentbanking.platform.security;

import com.agentbanking.platform.common.exception.ApiError;
import com.agentbanking.platform.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

/**
 * When the AI assistant (or any internal caller) presents {@code X-Internal-Api-Key}, the key must be
 * valid and the request must be GET-only. Mutations stay on human-facing authenticated flows.
 */
@Component
@RequiredArgsConstructor
public class InternalApiReadOnlyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Api-Key";

    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        if (key == null || key.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        String expected = properties.getAi().getApiKey();
        if (expected == null || expected.isBlank() || !constantTimeEquals(key, expected)) {
            write(response, request, HttpStatus.UNAUTHORIZED, "INVALID_INTERNAL_API_KEY",
                    "A valid " + HEADER + " header is required for internal AI callers");
            return;
        }
        if (!HttpMethod.GET.matches(request.getMethod()) && !HttpMethod.OPTIONS.matches(request.getMethod())
                && !HttpMethod.HEAD.matches(request.getMethod())) {
            write(response, request, HttpStatus.FORBIDDEN, "AI_READ_ONLY",
                    "Internal AI callers may only perform GET requests; mutations must go through authorised user actions");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void write(HttpServletResponse response, HttpServletRequest request, HttpStatus status, String code, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), code, message, request.getRequestURI(), null, null));
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
