package com.agentbanking.platform.ledger;

import com.agentbanking.platform.common.AppClock;
import com.agentbanking.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A balance-bearing account in the chart of accounts. The balance is a materialised running total
 * maintained exclusively by {@link LedgerService} in the same database transaction that writes the
 * corresponding immutable {@link LedgerEntry} rows.
 */
@Entity
@Table(name = "ledger_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LedgerAccount extends BaseEntity {

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EntrySide normalSide;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BalanceType balanceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OwnerType ownerType;

    private UUID agentId;

    private UUID areaId;

    private UUID customerId;

    @Column(nullable = false, length = 3)
    private String currency;

    @Setter(AccessLevel.PACKAGE)
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean allowNegative;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public LedgerAccount(String code, String name, BalanceType balanceType, UUID agentId, UUID areaId, UUID customerId, String currency) {
        this.code = code;
        this.name = name;
        this.balanceType = balanceType;
        this.category = balanceType.category();
        this.normalSide = balanceType.category().normalSide();
        this.ownerType = balanceType.ownerType();
        this.allowNegative = balanceType.allowNegative();
        this.agentId = agentId;
        this.areaId = areaId;
        this.customerId = customerId;
        this.currency = currency;
    }

    /** Change in balance produced by posting {@code amount} on {@code side}. */
    public BigDecimal signedDelta(EntrySide side, BigDecimal amount) {
        return side == normalSide ? amount : amount.negate();
    }

    @Override
    protected void onCreate() {
        createdAt = AppClock.now();
        updatedAt = createdAt;
    }

    @Override
    protected void onUpdate() {
        updatedAt = AppClock.now();
    }
}
