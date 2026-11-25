package com.agentbanking.platform.commission;

import com.agentbanking.platform.agent.Agent;
import com.agentbanking.platform.common.AppClock;
import com.agentbanking.platform.common.BaseEntity;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One payout of accrued commission, including any advance recovery deducted from it. */
@Entity
@Table(name = "commission_disbursements")
@Getter
@Setter
@NoArgsConstructor
public class CommissionDisbursement extends BaseEntity {

    @Column(nullable = false, unique = true, length = 40)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id")
    private Agent agent;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal grossCommission;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal principalRecovered;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal interestRecovered;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal netCommission;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal outstandingAdvanceBefore;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal outstandingAdvanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecoveryOrder recoveryOrder;

    @Column(nullable = false, precision = 9, scale = 4)
    private BigDecimal maxRecoveryPercent;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id")
    private FinancialTransaction transaction;

    private UUID processedBy;

    @Column(nullable = false)
    private Instant createdAt;

    @Override
    protected void onCreate() {
        createdAt = AppClock.now();
    }
}
