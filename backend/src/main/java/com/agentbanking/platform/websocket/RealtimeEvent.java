package com.agentbanking.platform.websocket;

import com.agentbanking.platform.common.AppClock;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event destined for WebSocket subscribers. Published inside a transaction and delivered
 * only after commit so clients never observe uncommitted balances.
 *
 * @param destination STOMP topic, or a user queue when {@code username} is set
 */
public record RealtimeEvent(String destination, String username, Envelope envelope) {

    public static RealtimeEvent topic(String destination, String eventType, Object payload) {
        return new RealtimeEvent(destination, null, Envelope.of(eventType, payload));
    }

    public static RealtimeEvent user(String username, String destination, String eventType, Object payload) {
        return new RealtimeEvent(destination, username, Envelope.of(eventType, payload));
    }

    public record Envelope(UUID eventId, String eventType, Instant timestamp, Object payload) {
        static Envelope of(String eventType, Object payload) {
            return new Envelope(UUID.randomUUID(), eventType, AppClock.now(), payload);
        }
    }

    public static final class Topics {
        private Topics() {
        }

        public static String agent(UUID agentId) {
            return "/topic/agents/" + agentId;
        }

        public static String area(UUID areaId) {
            return "/topic/areas/" + areaId;
        }

        public static final String RISK = "/topic/risk";
        public static final String FINANCE = "/topic/finance";
        public static final String USER_NOTIFICATIONS = "/queue/notifications";
    }
}
