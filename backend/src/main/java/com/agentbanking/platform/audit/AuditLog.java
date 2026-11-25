package com.agentbanking.platform.audit;

import com.agentbanking.platform.common.AppClock;
import com.agentbanking.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor
public class AuditLog extends BaseEntity {

    private UUID actorId;

    @Column(nullable = false)
    private String actorUsername;

    @Column(nullable = false)
    private String actorRole;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String entityType;

    private String entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> details;

    private String ipAddress;

    @Column(nullable = false)
    private Instant createdAt;

    public AuditLog(UUID actorId, String actorUsername, String actorRole, String action, String entityType,
                    String entityId, Map<String, Object> details, String ipAddress) {
        this.actorId = actorId;
        this.actorUsername = actorUsername;
        this.actorRole = actorRole;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = details;
        this.ipAddress = ipAddress;
    }

    @Override
    protected void onCreate() {
        createdAt = AppClock.now();
    }
}
