package com.agentbanking.platform.transaction;

import com.agentbanking.platform.common.AppClock;
import com.agentbanking.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** Configurable customer fee and agent commission rate for a transaction type and amount band. */
@Entity
@Table(name = "tariff_rules")
@Getter
@Setter
@NoArgsConstructor
public class TariffRule extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType transactionType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal minAmount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal maxAmount;

    @Column(nullable = false, precision = 9, scale = 4)
    private BigDecimal feePercent;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal feeFlat;

    @Column(nullable = false, precision = 9, scale = 4)
    private BigDecimal commissionPercent;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal commissionFlat;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private Instant updatedAt;

    private String updatedBy;

    @Version
    private Long version;

    public static TariffRule of(TransactionType type, String min, String max, String feePct, String feeFlat,
                                String commissionPct, String commissionFlat) {
        TariffRule r = new TariffRule();
        r.transactionType = type;
        r.minAmount = new BigDecimal(min);
        r.maxAmount = new BigDecimal(max);
        r.feePercent = new BigDecimal(feePct);
        r.feeFlat = new BigDecimal(feeFlat);
        r.commissionPercent = new BigDecimal(commissionPct);
        r.commissionFlat = new BigDecimal(commissionFlat);
        r.updatedBy = "system";
        return r;
    }

    @Override
    protected void onCreate() {
        updatedAt = AppClock.now();
    }

    @Override
    protected void onUpdate() {
        updatedAt = AppClock.now();
    }
}
