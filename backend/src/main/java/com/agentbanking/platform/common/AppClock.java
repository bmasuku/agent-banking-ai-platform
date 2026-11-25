package com.agentbanking.platform.common;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.function.Supplier;

/**
 * Single source of "now" for the platform. The demo seeder and simulations replay historical
 * activity by running real service calls at an overridden instant on the current thread.
 */
public final class AppClock {

    private static final ThreadLocal<Instant> OVERRIDE = new ThreadLocal<>();
    private static volatile Clock clock = Clock.systemUTC();
    private static volatile ZoneId businessZone = ZoneId.of("Africa/Harare");

    private AppClock() {
    }

    public static Instant now() {
        Instant override = OVERRIDE.get();
        return override != null ? override : clock.instant();
    }

    public static LocalDate today() {
        return LocalDate.ofInstant(now(), businessZone);
    }

    public static ZonedDateTime nowInZone() {
        return now().atZone(businessZone);
    }

    public static ZoneId zone() {
        return businessZone;
    }

    public static void setZone(ZoneId zone) {
        businessZone = zone;
    }

    public static void setClock(Clock newClock) {
        clock = newClock;
    }

    public static <T> T at(Instant instant, Supplier<T> action) {
        Instant previous = OVERRIDE.get();
        OVERRIDE.set(instant);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                OVERRIDE.remove();
            } else {
                OVERRIDE.set(previous);
            }
        }
    }

    public static void at(Instant instant, Runnable action) {
        at(instant, () -> {
            action.run();
            return null;
        });
    }
}
