package com.agentbanking.platform.exchange;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ExchangeExpiryScheduler {

    private final FloatExchangeService exchangeService;

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    public void expire() {
        int expired = exchangeService.expireStale();
        if (expired > 0) {
            log.info("Expired {} pending float exchange(s)", expired);
        }
    }
}
