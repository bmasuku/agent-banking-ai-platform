package com.agentbanking.platform.exchange;

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

@Entity
@Table(name = "float_exchange_interests")
@Getter
@Setter
@NoArgsConstructor
public class FloatExchangeInterest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exchange_id")
    private FloatExchange exchange;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "interested_agent_id")
    private Agent interestedAgent;

    @Column(nullable = false, length = 16)
    private String anonymityCode;

    @Column(length = 255)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ExchangeInterestStatus status = ExchangeInterestStatus.PENDING;

    @Column(nullable = false)
    private Instant createdAt;

    @Override
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = AppClock.now();
        }
    }
}
