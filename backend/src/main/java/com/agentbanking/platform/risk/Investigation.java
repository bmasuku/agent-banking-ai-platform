package com.agentbanking.platform.risk;

import com.agentbanking.platform.agent.Agent;
import com.agentbanking.platform.common.AppClock;
import com.agentbanking.platform.common.BaseEntity;
import com.agentbanking.platform.common.RiskLevel;
import com.agentbanking.platform.fraud.FraudCheck;
import com.agentbanking.platform.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "investigations")
@Getter
@Setter
@NoArgsConstructor
public class Investigation extends BaseEntity {

    @Column(nullable = false, unique = true, length = 32)
    private String caseNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id")
    private Agent agent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fraud_check_id")
    private FraudCheck fraudCheck;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskLevel severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvestigationStatus status = InvestigationStatus.OPEN;

    @Enumerated(EnumType.STRING)
    private InvestigationOutcome outcome;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> actionsTaken = new ArrayList<>();

    private String resolutionNotes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opened_by")
    private User openedBy;

    @Column(nullable = false)
    private Instant openedAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant closedAt;

    @Version
    private Long version;

    @Override
    protected void onCreate() {
        if (openedAt == null) {
            openedAt = AppClock.now();
        }
        updatedAt = AppClock.now();
    }

    @Override
    protected void onUpdate() {
        updatedAt = AppClock.now();
    }
}
