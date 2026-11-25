package com.agentbanking.platform.config;

import com.agentbanking.platform.common.AppClock;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;

@Configuration
@RequiredArgsConstructor
public class ClockConfig {

    private final AppProperties properties;

    @PostConstruct
    void configureBusinessZone() {
        AppClock.setZone(ZoneId.of(properties.getTimezone()));
    }
}
