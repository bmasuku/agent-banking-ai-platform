package com.agentbanking.platform.prediction;

import com.agentbanking.platform.agent.Agent;
import com.agentbanking.platform.ai.FeatureContribution;
import com.agentbanking.platform.common.AppClock;
import com.agentbanking.platform.common.BaseEntity;
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

@Entity
@Table(name = "float_predictions")
@Getter
@Setter
@NoArgsConstructor
public class FloatPrediction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id")
    private Agent agent;

    @Column(nullable = false)
    private int horizonHours;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal currentEfloat;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal currentCash;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal projectedEfloat;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal projectedCash;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal minProjectedEfloat;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal minProjectedCash;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal predictedEfloatNetFlow;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal predictedCashNetFlow;

    @Column(precision = 8, scale = 2)
    private BigDecimal hoursToEfloatStockout;

    @Column(precision = 8, scale = 2)
    private BigDecimal hoursToCashStockout;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LiquidityRisk riskType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal recommendedEfloatTopup;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal recommendedCashTopup;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal confidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<TrajectoryPoint> trajectory;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<FeatureContribution> drivers;

    @Column(nullable = false)
    private String explanation;

    @Column(nullable = false)
    private String modelSource;

    @Column(nullable = false)
    private String modelVersion;

    @Column(nullable = false)
    private Instant createdAt;

    @Override
    protected void onCreate() {
        createdAt = AppClock.now();
    }

    /** Projected balances at the end of one forecast hour, with an uncertainty band. */
    public record TrajectoryPoint(Instant hour, BigDecimal efloat, BigDecimal efloatLower, BigDecimal efloatUpper,
                                  BigDecimal cash, BigDecimal cashLower, BigDecimal cashUpper) {
    }
}
