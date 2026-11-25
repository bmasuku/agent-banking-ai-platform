package com.agentbanking.platform.agent;

import com.agentbanking.platform.common.AppClock;
import com.agentbanking.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The agent's financial account. Every money movement involving an agent first locks this row,
 * which serialises concurrent operations per agent and makes idempotency checks race-free.
 */
@Entity
@Table(name = "agent_accounts")
@Getter
@Setter
@NoArgsConstructor
public class AgentAccount extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id", unique = true)
    private Agent agent;

    @Column(nullable = false, unique = true, length = 32)
    private String accountNumber;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentAccountStatus status = AgentAccountStatus.ACTIVE;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal dailyTransactionLimit;

    @Column(nullable = false)
    private Instant openedAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @Override
    protected void onCreate() {
        openedAt = AppClock.now();
        updatedAt = openedAt;
    }

    @Override
    protected void onUpdate() {
        updatedAt = AppClock.now();
    }
}
