package com.agentbanking.platform.simulation;

import com.agentbanking.platform.common.AppClock;
import com.agentbanking.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "simulation_runs")
@Getter
@Setter
@NoArgsConstructor
public class SimulationRun extends BaseEntity {

    public enum Status { RUNNING, COMPLETED, FAILED }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SimulationScenario scenario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.RUNNING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> parameters = new HashMap<>();

    @Column(nullable = false)
    private int transactionsAttempted;

    @Column(nullable = false)
    private int transactionsCompleted;

    @Column(nullable = false)
    private int transactionsBlocked;

    @Column(nullable = false)
    private int transactionsFailed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> summary = new HashMap<>();

    @Column(columnDefinition = "text")
    private String errorMessage;

    private UUID startedBy;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant finishedAt;

    @Override
    protected void onCreate() {
        if (startedAt == null) {
            startedAt = AppClock.now();
        }
    }
}
