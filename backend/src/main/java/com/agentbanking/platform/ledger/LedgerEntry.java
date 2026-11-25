package com.agentbanking.platform.ledger;

import com.agentbanking.platform.common.BaseEntity;
import com.agentbanking.platform.transaction.FinancialTransaction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;

/** One immutable debit or credit line of a journal. Updates and deletes are rejected by a database trigger. */
@Entity
@Immutable
@Table(name = "ledger_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LedgerEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id")
    private FinancialTransaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id")
    private LedgerAccount account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EntrySide side;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    @Column(nullable = false)
    private int lineNumber;

    private String memo;

    @Column(nullable = false)
    private Instant createdAt;

    LedgerEntry(FinancialTransaction transaction, LedgerAccount account, EntrySide side, BigDecimal amount,
                BigDecimal balanceAfter, int lineNumber, String memo, Instant createdAt) {
        this.transaction = transaction;
        this.account = account;
        this.side = side;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.lineNumber = lineNumber;
        this.memo = memo;
        this.createdAt = createdAt;
    }
}
