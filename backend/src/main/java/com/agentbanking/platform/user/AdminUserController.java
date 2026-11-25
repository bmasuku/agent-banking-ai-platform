package com.agentbanking.platform.user;

import com.agentbanking.platform.common.PageResponse;
import com.agentbanking.platform.security.UserPrincipal;
import com.agentbanking.platform.user.UserDtos.CreateUserRequest;
import com.agentbanking.platform.user.UserDtos.ResetPasswordRequest;
import com.agentbanking.platform.user.UserDtos.UpdateUserRequest;
import com.agentbanking.platform.user.UserDtos.UpdateUserStatusRequest;
import com.agentbanking.platform.user.UserDtos.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin - Users", description = "User account management")
public class AdminUserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "Search users")
    public PageResponse<UserResponse> search(@RequestParam(required = false) Role role,
                                             @RequestParam(required = false) UserStatus status,
                                             @RequestParam(required = false) String search,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return userService.search(role, status, search, PageRequest.of(page, Math.min(size, 100), Sort.by("username")));
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable UUID id) {
        return userService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a staff user (AREA_MANAGER, RISK_OFFICER, FINANCE_OFFICER, ADMIN)")
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request);
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(id, request);
    }

    @PostMapping("/{id}/status")
    @Operation(summary = "Activate, suspend or lock a user account")
    public UserResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateUserStatusRequest request,
                                     @AuthenticationPrincipal UserPrincipal principal) {
        return userService.updateStatus(id, request.status(), request.reason(), principal.getId());
    }

    @PostMapping("/{id}/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable UUID id, @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(id, request.newPassword());
    }
}
