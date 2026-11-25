package com.agentbanking.platform.auth;

import com.agentbanking.platform.user.Role;
import com.agentbanking.platform.user.UserDtos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record LogoutRequest(String refreshToken, boolean allSessions) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Pattern(regexp = UserDtos.PASSWORD_PATTERN, message = UserDtos.PASSWORD_MESSAGE) String newPassword) {
    }

    public record AuthenticatedUser(
            UUID id,
            String username,
            String fullName,
            String email,
            Role role,
            UUID areaId,
            String areaName,
            UUID agentId,
            String agentCode,
            String client) {
    }

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            long refreshExpiresIn,
            AuthenticatedUser user) {
    }
}
