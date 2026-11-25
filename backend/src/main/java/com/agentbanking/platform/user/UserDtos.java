package com.agentbanking.platform.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class UserDtos {

    public static final String PASSWORD_PATTERN = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";
    public static final String PASSWORD_MESSAGE = "Password must be 8-72 characters and contain at least one letter and one digit";

    private UserDtos() {
    }

    public record UserResponse(
            UUID id,
            String username,
            String email,
            String fullName,
            String phoneNumber,
            Role role,
            UserStatus status,
            UUID areaId,
            String areaName,
            int failedLoginAttempts,
            Instant lastLoginAt,
            Instant createdAt) {

        public static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getFullName(),
                    user.getPhoneNumber(), user.getRole(), user.getStatus(),
                    user.getArea() != null ? user.getArea().getId() : null,
                    user.getArea() != null ? user.getArea().getName() : null,
                    user.getFailedLoginAttempts(), user.getLastLoginAt(), user.getCreatedAt());
        }
    }

    public record CreateUserRequest(
            @NotBlank @Size(min = 3, max = 64) @Pattern(regexp = "^[a-zA-Z0-9._-]+$") String username,
            @NotBlank @Email String email,
            @NotBlank @Size(max = 160) String fullName,
            @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "Phone number must contain 9-15 digits") String phoneNumber,
            @NotNull Role role,
            UUID areaId,
            @NotBlank @Pattern(regexp = PASSWORD_PATTERN, message = PASSWORD_MESSAGE) String password) {
    }

    public record UpdateUserRequest(
            @Email String email,
            @Size(max = 160) String fullName,
            @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "Phone number must contain 9-15 digits") String phoneNumber,
            UUID areaId) {
    }

    public record UpdateUserStatusRequest(@NotNull UserStatus status, @Size(max = 255) String reason) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Pattern(regexp = PASSWORD_PATTERN, message = PASSWORD_MESSAGE) String newPassword) {
    }
}
