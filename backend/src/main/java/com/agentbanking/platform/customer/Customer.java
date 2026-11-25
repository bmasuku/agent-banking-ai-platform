package com.agentbanking.platform.customer;

import com.agentbanking.platform.agent.Agent;
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

import java.time.Instant;

/** A simulated retail customer served at agent outlets; owns a CUSTOMER_WALLET ledger account. */
@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
public class Customer extends BaseEntity {

    public enum KycLevel { BASIC, STANDARD, FULL }

    public enum Status { ACTIVE, BLOCKED }

    @Column(nullable = false, unique = true, length = 32)
    private String customerNumber;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true, length = 32)
    private String phoneNumber;

    private String nationalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KycLevel kycLevel = KycLevel.BASIC;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registered_by_agent_id")
    private Agent registeredByAgent;

    @Column(nullable = false)
    private Instant createdAt;

    @Override
    protected void onCreate() {
        createdAt = AppClock.now();
    }
}
