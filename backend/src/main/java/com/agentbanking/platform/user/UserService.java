package com.agentbanking.platform.user;

import com.agentbanking.platform.area.Area;
import com.agentbanking.platform.area.AreaRepository;
import com.agentbanking.platform.audit.AuditService;
import com.agentbanking.platform.auth.RefreshTokenRepository;
import com.agentbanking.platform.common.AppClock;
import com.agentbanking.platform.common.PageResponse;
import com.agentbanking.platform.common.exception.BusinessException;
import com.agentbanking.platform.user.UserDtos.CreateUserRequest;
import com.agentbanking.platform.user.UserDtos.UpdateUserRequest;
import com.agentbanking.platform.user.UserDtos.UserResponse;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AreaRepository areaRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(Role role, UserStatus status, String search, Pageable pageable) {
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("username")), like),
                        cb.like(cb.lower(root.get("fullName")), like),
                        cb.like(cb.lower(root.get("email")), like)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.of(userRepository.findAll(spec, pageable), UserResponse::from);
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID id) {
        return UserResponse.from(require(id));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (request.role() == Role.AGENT) {
            throw BusinessException.badRequest("USE_AGENT_ONBOARDING",
                    "Agent users are created through POST /api/v1/admin/agents so their ledger accounts are provisioned");
        }
        User user = buildUser(request.username(), request.email(), request.fullName(), request.phoneNumber(),
                request.role(), request.password(), request.areaId());
        userRepository.save(user);
        auditService.record("USER_CREATED", "USER", user.getId(),
                Map.of("username", user.getUsername(), "role", user.getRole().name()));
        return UserResponse.from(user);
    }

    /** Shared by agent onboarding and the demo seeder. */
    @Transactional
    public User buildUser(String username, String email, String fullName, String phone, Role role, String rawPassword, UUID areaId) {
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw BusinessException.conflict("USERNAME_TAKEN", "Username already exists: " + username);
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw BusinessException.conflict("EMAIL_TAKEN", "Email already exists: " + email);
        }
        Area area = null;
        if (areaId != null) {
            area = areaRepository.findById(areaId).orElseThrow(() -> BusinessException.notFound("Area", areaId));
        }
        if (role == Role.AREA_MANAGER && area == null) {
            throw BusinessException.badRequest("AREA_REQUIRED", "Area managers must be assigned to an area");
        }
        User user = new User();
        user.setUsername(username.toLowerCase());
        user.setEmail(email.toLowerCase());
        user.setFullName(fullName);
        user.setPhoneNumber(phone);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setArea(role == Role.AREA_MANAGER || role == Role.AGENT ? area : null);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        return user;
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        User user = require(id);
        if (request.email() != null && !request.email().equalsIgnoreCase(user.getEmail())) {
            if (userRepository.existsByEmailIgnoreCase(request.email())) {
                throw BusinessException.conflict("EMAIL_TAKEN", "Email already exists");
            }
            user.setEmail(request.email().toLowerCase());
        }
        if (request.fullName() != null) {
            user.setFullName(request.fullName());
        }
        if (request.phoneNumber() != null) {
            user.setPhoneNumber(request.phoneNumber());
        }
        if (request.areaId() != null) {
            if (user.getRole() != Role.AREA_MANAGER) {
                throw BusinessException.badRequest("AREA_NOT_APPLICABLE", "Only area managers are assigned to areas through this endpoint");
            }
            user.setArea(areaRepository.findById(request.areaId())
                    .orElseThrow(() -> BusinessException.notFound("Area", request.areaId())));
        }
        auditService.record("USER_UPDATED", "USER", user.getId(), Map.of("username", user.getUsername()));
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateStatus(UUID id, UserStatus status, String reason, UUID actorId) {
        User user = require(id);
        if (user.getId().equals(actorId) && status != UserStatus.ACTIVE) {
            throw BusinessException.badRequest("CANNOT_DISABLE_SELF", "You cannot suspend or lock your own account");
        }
        UserStatus previous = user.getStatus();
        user.setStatus(status);
        if (status == UserStatus.ACTIVE) {
            user.setFailedLoginAttempts(0);
        } else {
            refreshTokenRepository.revokeAllForUser(user.getId(), AppClock.now(), "ACCOUNT_" + status.name());
        }
        auditService.record("USER_STATUS_CHANGED", "USER", user.getId(),
                Map.of("from", previous.name(), "to", status.name(), "reason", reason == null ? "" : reason));
        return UserResponse.from(user);
    }

    @Transactional
    public void resetPassword(UUID id, String newPassword) {
        User user = require(id);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(AppClock.now());
        user.setFailedLoginAttempts(0);
        if (user.getStatus() == UserStatus.LOCKED) {
            user.setStatus(UserStatus.ACTIVE);
        }
        refreshTokenRepository.revokeAllForUser(user.getId(), AppClock.now(), "PASSWORD_RESET");
        auditService.record("USER_PASSWORD_RESET", "USER", user.getId(), Map.of("username", user.getUsername()));
    }

    @Transactional(readOnly = true)
    public User require(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> BusinessException.notFound("User", id));
    }
}
