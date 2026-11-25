package com.agentbanking.platform.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Best-effort recording of human decisions against AI prediction ids. Failures are logged and never
 * block the platform decision path.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiGovernanceFeedbackClient {

    private final AiServiceClient aiClient;

    @Async
    public void recordDecision(String predictionId, String decision, String decidedBy, String decidedByRole,
                               String notes, String sourceReference) {
        if (predictionId == null || predictionId.isBlank() || !aiClient.isEnabled()) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("decision", decision);
        body.put("decided_by", decidedBy);
        body.put("decided_by_role", decidedByRole);
        body.put("notes", notes);
        body.put("source_reference", sourceReference);
        try {
            aiClient.post("/internal/ai/v1/governance/predictions/" + predictionId + "/decisions", body, Map.class);
        } catch (Exception ex) {
            log.warn("Failed to record AI governance decision for {}: {}", predictionId, ex.getMessage());
        }
    }
}
