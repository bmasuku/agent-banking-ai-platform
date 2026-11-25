package com.agentbanking.platform.common.exception;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String errorCode,
        String message,
        String path,
        Map<String, Object> details,
        List<FieldViolation> fieldErrors) {

    public record FieldViolation(String field, String message) {
    }
}
