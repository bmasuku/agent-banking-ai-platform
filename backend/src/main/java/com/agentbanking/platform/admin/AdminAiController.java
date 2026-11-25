package com.agentbanking.platform.admin;

import com.agentbanking.platform.ai.AiServiceClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/ai")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - AI Governance", description = "Pass-through proxies to the AI service governance store")
public class AdminAiController {

    private final AiServiceClient aiServiceClient;

    @GetMapping("/models")
    @Operation(summary = "Model registry (versions, methods, evaluations)")
    public List<?> models() {
        return aiServiceClient.getRequired("/internal/ai/v1/governance/models", List.class);
    }

    @GetMapping("/predictions")
    @Operation(summary = "List stored AI predictions (newest first)")
    public Map<?, ?> predictions(@RequestParam(required = false) String kind,
                                 @RequestParam(required = false) String subjectId,
                                 @RequestParam(defaultValue = "50") int limit,
                                 @RequestParam(defaultValue = "0") int offset) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("kind", kind);
        query.put("subject_id", subjectId);
        query.put("limit", limit);
        query.put("offset", offset);
        return aiServiceClient.getRequired("/internal/ai/v1/governance/predictions", query, Map.class);
    }

    @GetMapping("/predictions/{predictionId}")
    public Map<?, ?> prediction(@PathVariable String predictionId) {
        return aiServiceClient.getRequired("/internal/ai/v1/governance/predictions/" + predictionId, Map.class);
    }

    @GetMapping("/summary")
    public Map<?, ?> summary() {
        return aiServiceClient.getRequired("/internal/ai/v1/governance/summary", Map.class);
    }
}