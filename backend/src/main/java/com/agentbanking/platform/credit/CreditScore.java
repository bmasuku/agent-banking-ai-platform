package com.agentbanking.platform.credit;

import com.agentbanking.platform.agent.Agent;
import com.agentbanking.platform.ai.FeatureContribution;
import com.agentbanking.platform.common.AppClock;
import com.agentbanking.platform.common.BaseEntity;
import com.agentbanking.platform.common.RiskLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "credit_scores")
@Getter
@Setter
@NoArgsConstructor
public class CreditScore extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id")
    private Agent agent;

    @Column(nullable = false)
    private int score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskLevel riskBand;

    @Column(nullable = false, precision = 6, scale = 4)
    private BigDecimal probabilityOfDefault;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal recommendedLimit;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal recommendedDailyRate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<FeatureContribution> factors;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> features;

    @Column(nullable = false)
    private String explanation;

    @Column(nullable = false)
    private String modelSource;

    @Column(nullable = false)
    private String modelVersion;

    @Column(length = 24)
    private String recommendation;

    private Boolean eligible;

    @Column(length = 64)
    private String aiPredictionId;

    @Column(nullable = false)
    private Instant createdAt;

    @Override
    protected void onCreate() {
        createdAt = AppClock.now();
    }
}
