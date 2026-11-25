package com.agentbanking.platform.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimePublisher {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEvent(RealtimeEvent event) {
        try {
            if (event.username() != null) {
                messagingTemplate.convertAndSendToUser(event.username(), event.destination(), event.envelope());
            } else {
                messagingTemplate.convertAndSend(event.destination(), event.envelope());
            }
        } catch (Exception ex) {
            log.warn("Failed to deliver realtime event {} to {}: {}", event.envelope().eventType(), event.destination(), ex.getMessage());
        }
    }
}
