package com.agentbanking.platform.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Map;

@Getter
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;
    private final Map<String, Object> details;

    public BusinessException(HttpStatus status, String errorCode, String message) {
        this(status, errorCode, message, Map.of());
    }

    public BusinessException(HttpStatus status, String errorCode, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.details = details == null ? Map.of() : details;
    }

    public static BusinessException notFound(String entity, Object id) {
        return new BusinessException(HttpStatus.NOT_FOUND, entity.toUpperCase().replace(' ', '_') + "_NOT_FOUND",
                entity + " not found: " + id);
    }

    public static BusinessException badRequest(String code, String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static BusinessException conflict(String code, String message) {
        return new BusinessException(HttpStatus.CONFLICT, code, message);
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", message);
    }

    public static BusinessException unprocessable(String code, String message) {
        return new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }

    public static BusinessException unprocessable(String code, String message, Map<String, Object> details) {
        return new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, code, message, details);
    }

    public static BusinessException serviceUnavailable(String code, String message) {
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }
}
