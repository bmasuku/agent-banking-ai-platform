package com.agentbanking.platform.audit;

import com.agentbanking.platform.common.PageResponse;
import com.agentbanking.platform.security.UserPrincipal;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Append-only audit trail. Records join the caller's transaction so they commit or roll back with the action. */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;

    @Transactional
    public void record(String action, String entityType, Object entityId, Map<String, ?> details) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal p) {
            recordAs(p.getId(), p.getUsername(), p.getRole().name(), action, entityType,
                    entityId == null ? null : entityId.toString(), details, currentIp());
        } else {
            recordAs(null, "system", "SYSTEM", action, entityType, entityId == null ? null : entityId.toString(), details, null);
        }
    }

    @Transactional
    public void recordAs(UUID actorId, String username, String role, String action, String entityType, String entityId,
                         Map<String, ?> details, String ip) {
        Map<String, Object> copy = new HashMap<>();
        if (details != null) {
            details.forEach((k, v) -> copy.put(k, v == null ? null : (v instanceof Number || v instanceof Boolean ? v : v.toString())));
        }
        repository.save(new AuditLog(actorId, username, role, action, entityType, entityId, copy, ip != null ? ip : currentIp()));
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(String action, String entityType, String entityId, UUID actorId,
                                                 Instant from, Instant to, Pageable pageable) {
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (action != null && !action.isBlank()) {
                p.add(cb.equal(root.get("action"), action));
            }
            if (entityType != null && !entityType.isBlank()) {
                p.add(cb.equal(root.get("entityType"), entityType));
            }
            if (entityId != null && !entityId.isBlank()) {
                p.add(cb.equal(root.get("entityId"), entityId));
            }
            if (actorId != null) {
                p.add(cb.equal(root.get("actorId"), actorId));
            }
            if (from != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                p.add(cb.lessThan(root.get("createdAt"), to));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return PageResponse.of(repository.findAll(spec, pageable), AuditLogResponse::from);
    }

    private String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            return forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
        }
        return null;
    }

    public record AuditLogResponse(UUID id, UUID actorId, String actorUsername, String actorRole, String action,
                                   String entityType, String entityId, Map<String, Object> details, String ipAddress,
                                   Instant createdAt) {
        static AuditLogResponse from(AuditLog log) {
            return new AuditLogResponse(log.getId(), log.getActorId(), log.getActorUsername(), log.getActorRole(),
                    log.getAction(), log.getEntityType(), log.getEntityId(), log.getDetails(), log.getIpAddress(), log.getCreatedAt());
        }
    }
}
