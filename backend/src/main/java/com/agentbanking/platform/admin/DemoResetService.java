package com.agentbanking.platform.admin;

import com.agentbanking.platform.admin.DemoDataSeeder.SeedSummary;
import com.agentbanking.platform.audit.AuditService;
import com.agentbanking.platform.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wipes all operational data and rebuilds the demonstration dataset. TRUNCATE bypasses the row-level
 * immutability triggers by design; this is the only code path that removes ledger history and it is
 * restricted to administrators of a simulation environment.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoResetService {

    private static final List<String> TABLES = List.of(
            "simulation_runs", "investigation_notes", "investigations", "fraud_checks", "credit_scores", "float_predictions",
            "notifications", "audit_logs", "advance_repayments", "float_advances", "commissions", "commission_disbursements",
            "float_exchanges", "float_replenishments", "ledger_entries", "financial_transactions", "ledger_accounts",
            "customers", "agent_accounts", "agents", "refresh_tokens", "revoked_access_tokens", "users", "areas",
            "tariff_rules", "system_settings");

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final PlatformBootstrap bootstrap;
    private final DemoDataSeeder seeder;
    private final AuditService auditService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public record ResetResult(SeedSummary seed, String message) {
    }

    public ResetResult reset(String requestedBy) {
        if (!running.compareAndSet(false, true)) {
            throw BusinessException.conflict("RESET_IN_PROGRESS", "A demo reset is already running");
        }
        try {
            log.warn("Demo reset requested by {}", requestedBy);
            transactionTemplate.executeWithoutResult(status -> {
                jdbcTemplate.execute("TRUNCATE TABLE " + String.join(", ", TABLES) + " CASCADE");
                jdbcTemplate.execute("ALTER SEQUENCE investigation_case_seq RESTART WITH 1");
            });
            bootstrap.initializeReferenceData();
            SeedSummary summary = seeder.seed();
            transactionTemplate.executeWithoutResult(status -> auditService.recordAs(null, requestedBy, "ADMIN", "DEMO_RESET",
                    "PLATFORM", null, Map.of("transactions", summary.transactionsCompleted(), "agents", summary.agents()), null));
            return new ResetResult(summary, "Demo data rebuilt. All sessions were invalidated; sign in again with the demo credentials.");
        } finally {
            running.set(false);
        }
    }
}
