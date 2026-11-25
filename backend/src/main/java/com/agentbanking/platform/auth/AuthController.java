package com.agentbanking.platform.auth;

import com.agentbanking.platform.auth.AuthDtos.AuthenticatedUser;
import com.agentbanking.platform.auth.AuthDtos.ChangePasswordRequest;
import com.agentbanking.platform.auth.AuthDtos.LoginRequest;
import com.agentbanking.platform.auth.AuthDtos.LogoutRequest;
import com.agentbanking.platform.auth.AuthDtos.RefreshRequest;
import com.agentbanking.platform.auth.AuthDtos.TokenResponse;
import com.agentbanking.platform.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login, token refresh, logout and password management")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Authenticate and receive an access token and a rotating refresh token")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request.username(), request.password(), clientIp(http));
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(summary = "Exchange a refresh token for a new token pair (the old refresh token is revoked)")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest http) {
        return authService.refresh(request.refreshToken(), clientIp(http));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke the current access token and refresh token family (or all sessions)")
    public void logout(@AuthenticationPrincipal UserPrincipal principal, @RequestBody(required = false) LogoutRequest request) {
        authService.logout(principal, request != null ? request.refreshToken() : null, request != null && request.allSessions());
    }

    @GetMapping("/me")
    @Operation(summary = "Current authenticated user profile")
    public AuthenticatedUser me(@AuthenticationPrincipal UserPrincipal principal) {
        return authService.me(principal);
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(principal, request.currentPassword(), request.newPassword());
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }
}
