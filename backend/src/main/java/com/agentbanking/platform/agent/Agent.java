package com.agentbanking.platform.agent;

import com.agentbanking.platform.area.Area;
import com.agentbanking.platform.common.AppClock;
import com.agentbanking.platform.common.BaseEntity;
import com.agentbanking.platform.common.RiskLevel;
import com.agentbanking.platform.user.User;
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
@Table(name = "agents")
@Getter
@Setter
@NoArgsConstructor
public class Agent extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @Column(nullable = false, unique = true, length = 24)
    private String agentCode;

    @Column(nullable = false)
    private String businessName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_id")
    private Area area;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentTier tier = AgentTier.STANDARD;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentStatus status = AgentStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskLevel riskLevel = RiskLevel.MEDIUM;

    @Column(nullable = false)
    private int creditScore = 500;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal creditLimit = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 6)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 6)
    private BigDecimal longitude;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal minEfloatThreshold;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal minCashThreshold;

    @Column(nullable = false)
    private Instant onboardedAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @Override
    protected void onCreate() {
        if (onboardedAt == null) {
            onboardedAt = AppClock.now();
        }
        updatedAt = AppClock.now();
    }

    @Override
    protected void onUpdate() {
        updatedAt = AppClock.now();
    }
}
