package com.agentbanking.platform.fieldvisit;

import com.agentbanking.platform.agent.Agent;
import com.agentbanking.platform.area.Area;
import com.agentbanking.platform.common.AppClock;
import com.agentbanking.platform.common.BaseEntity;
import com.agentbanking.platform.user.User;
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

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "field_visits")
@Getter
@Setter
@NoArgsConstructor
public class FieldVisit extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id")
    private Agent agent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_id")
    private Area area;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_manager_id")
    private User areaManager;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private VisitType visitType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String notes;

    @Column(precision = 10, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 6)
    private BigDecimal longitude;

    @Column(nullable = false)
    private boolean locationSimulated;

    @Column(precision = 19, scale = 4)
    private BigDecimal verifiedCashBalance;

    @Column(name = "ledger_cash_at_visit", precision = 19, scale = 4)
    private BigDecimal ledgerCashAtVisit;

    @Column(precision = 19, scale = 4)
    private BigDecimal discrepancyAmount;

    @Column(length = 64)
    private String clientLocalId;

    @Column(nullable = false)
    private Instant visitedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Override
    protected void onCreate() {
        Instant now = AppClock.now();
        if (visitedAt == null) {
            visitedAt = now;
        }
        createdAt = now;
    }
}
