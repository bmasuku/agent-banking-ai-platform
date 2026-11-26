package com.agentbanking.platform.exchange;

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
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "float_exchanges")
@Getter
@Setter
@NoArgsConstructor
public class FloatExchange extends BaseEntity {

    @Column(nullable = false, unique = true, length = 40)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_agent_id")
    private Agent requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counterparty_agent_id")
    private Agent counterparty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExchangeDirection direction;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExchangeStatus status = ExchangeStatus.OPEN;

    private String note;

    private String responseNote;

    /** Public handle shown on the anonymous board (e.g. NX-4F2A). */
    @Column(length = 16)
    private String anonymityCode;

    /** Shown privately to the requester; counterparty enters this to verify meetup. */
    @Column(length = 12)
    private String requesterVerifyToken;

    /** Shown privately to the counterparty; requester enters this to verify meetup. */
    @Column(length = 12)
    private String counterpartyVerifyToken;

    private Instant requesterVerifiedAt;

    private Instant counterpartyVerifiedAt;

    private Instant matchedAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private FinancialTransaction transaction;

    @Column(unique = true, length = 120)
    private String idempotencyKey;

    @Column(nullable = false)
    private Instant requestedAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant respondedAt;

    @Version
    private Long version;

    @Override
    protected void onCreate() {
        if (requestedAt == null) {
            requestedAt = AppClock.now();
        }
    }
}
