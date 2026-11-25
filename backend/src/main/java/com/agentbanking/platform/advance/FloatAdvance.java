package com.agentbanking.platform.advance;

import com.agentbanking.platform.agent.Agent;
import com.agentbanking.platform.common.AppClock;
import com.agentbanking.platform.common.BaseEntity;
import com.agentbanking.platform.common.Money;
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
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Short-term float credit. Outstanding fields mirror the agent's ADVANCE_PRINCIPAL / ADVANCE_INTEREST
 * ledger accounts and are only changed in the same transaction as the corresponding ledger posting.
 */
@Entity
@Table(name = "float_advances")
@Getter
@Setter
@NoArgsConstructor
public class FloatAdvance extends BaseEntity {

    @Column(nullable = false, unique = true, length = 40)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id")
    private Agent agent;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal requestedAmount;

    @Column(precision = 19, scale = 4)
    private BigDecimal approvedAmount;

    @Column(precision = 9, scale = 6)
    private BigDecimal dailyInterestRate;

    @Column(nullable = false)
    private int termDays;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal interestAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal penaltyInterestAccrued = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal principalOutstanding = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal interestOutstanding = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal principalRepaid = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal interestRepaid = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AdvanceStatus status = AdvanceStatus.PENDING;

    @Enumerated(EnumType.STRING)
    private AdvanceDecisionType decisionType;

    private Integer creditScoreAtRequest;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> decisionRationale = new HashMap<>();

    private String purpose;

    private String decisionNote;

    private UUID decidedBy;

    @Column(nullable = false)
    private Instant requestedAt;

    private Instant decidedAt;

    private Instant disbursedAt;

    private LocalDate dueDate;

    private LocalDate lastPenaltyAccrualDate;

    private Instant closedAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "disbursement_transaction_id")
    private FinancialTransaction disbursementTransaction;

    @Column(unique = true, length = 120)
    private String idempotencyKey;

    @Version
    private Long version;

    @Override
    protected void onCreate() {
        if (requestedAt == null) {
            requestedAt = AppClock.now();
        }
    }

    public BigDecimal totalOutstanding() {
        return Money.of(principalOutstanding.add(interestOutstanding));
    }
}
