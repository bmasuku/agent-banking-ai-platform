package com.agentbanking.platform.fraud;

import com.agentbanking.platform.agent.Agent;
import com.agentbanking.platform.ai.FeatureContribution;
import com.agentbanking.platform.common.AppClock;
import com.agentbanking.platform.common.BaseEntity;
import com.agentbanking.platform.common.RiskLevel;
import com.agentbanking.platform.transaction.FinancialTransaction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persisted, explainable fraud evaluation for one transaction attempt. */
@Entity
@Table(name = "fraud_checks")
@Getter
@Setter
@NoArgsConstructor
public class FraudCheck extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", unique = true)
    private FinancialTransaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id")
    private Agent agent;

    @Column(nullable = false, precision = 6, scale = 4)
    private BigDecimal score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskLevel riskLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FraudDecision decision;

    @Column(nullable = false)
    private boolean flagged;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewStatus reviewStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> reasonCodes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<FeatureContribution> featureContributions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> features;

    @Column(nullable = false)
    private String explanation;

    @Column(nullable = false)
    private String modelSource;

    @Column(nullable = false)
    private String modelVersion;

    @Column(length = 40)
    private String recommendedAction;

    @Column(length = 64)
    private String aiPredictionId;

    private UUID reviewedBy;

    private String reviewNote;

    private Instant reviewedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Version
    private Long version;

    @Override
    protected void onCreate() {
        createdAt = AppClock.now();
    }
}
