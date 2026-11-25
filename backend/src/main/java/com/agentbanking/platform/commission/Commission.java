package com.agentbanking.platform.commission;

import com.agentbanking.platform.agent.Agent;
import com.agentbanking.platform.common.AppClock;
import com.agentbanking.platform.common.BaseEntity;
import com.agentbanking.platform.transaction.FinancialTransaction;
import com.agentbanking.platform.transaction.TransactionType;
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

import java.math.BigDecimal;
import java.time.Instant;

/** Commission earned on one customer transaction; accrued to COMMISSION_PAYABLE until disbursed. */
@Entity
@Table(name = "commissions")
@Getter
@Setter
@NoArgsConstructor
public class Commission extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id")
    private Agent agent;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", unique = true)
    private FinancialTransaction transaction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType transactionType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal baseAmount;

    @Column(nullable = false, precision = 9, scale = 4)
    private BigDecimal ratePercent;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal flatAmount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CommissionStatus status = CommissionStatus.ACCRUED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "disbursement_id")
    private CommissionDisbursement disbursement;

    @Column(nullable = false)
    private Instant accruedAt;

    @Version
    private Long version;

    @Override
    protected void onCreate() {
        if (accruedAt == null) {
            accruedAt = AppClock.now();
        }
    }
}
