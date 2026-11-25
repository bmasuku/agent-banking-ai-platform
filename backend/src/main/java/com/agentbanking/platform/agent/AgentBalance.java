package com.agentbanking.platform.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Read-only per-agent balance summary backed by the {@code agent_balances} view, which pivots the
 * agent's ledger accounts. Balances are therefore always derived from the ledger, never stored twice.
 */
@Entity
@Immutable
@Subselect("select * from agent_balances")
@Synchronize({"ledger_accounts", "agents"})
@Getter
public class AgentBalance {

    @Id
    @Column(name = "agent_id")
    private UUID agentId;

    @Column(name = "efloat_balance")
    private BigDecimal efloatBalance;

    @Column(name = "cash_balance")
    private BigDecimal cashBalance;

    @Column(name = "commission_payable")
    private BigDecimal commissionPayable;

    @Column(name = "advance_principal_outstanding")
    private BigDecimal advancePrincipalOutstanding;

    @Column(name = "advance_interest_outstanding")
    private BigDecimal advanceInterestOutstanding;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
