package com.agentbanking.platform.advance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AdvancePenaltyScheduler {

    private final FloatAdvanceService advanceService;

    @Scheduled(cron = "${app.scheduling.penalty-accrual-cron}", zone = "${app.timezone}")
    public void accrue() {
        int processed = advanceService.accruePenalties();
        if (processed > 0) {
            log.info("Penalty accrual processed {} overdue advance(s)", processed);
        }
    }
}
