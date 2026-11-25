package com.agentbanking.platform.prediction;

import com.agentbanking.platform.agent.AgentService;
import com.agentbanking.platform.prediction.FloatPredictionService.PredictionResponse;
import com.agentbanking.platform.security.AccessControlService;
import com.agentbanking.platform.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/predictions/agents/{agentId}")
@RequiredArgsConstructor
@Tag(name = "Float Predictions", description = "Hourly e-float/cash forecasts with confidence bands, stock-out times and top-up recommendations")
public class FloatPredictionController {

    private final FloatPredictionService predictionService;
    private final AgentService agentService;
    private final AccessControlService access;

    @GetMapping
    @Operation(summary = "Latest float prediction for the agent")
    public PredictionResponse latest(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID agentId) {
        access.assertCanViewAgent(principal, agentService.require(agentId));
        return predictionService.latest(agentId);
    }

    @PostMapping
    @Operation(summary = "Generate a fresh prediction (AI service, or heuristic forecaster when unavailable)")
    public PredictionResponse predict(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID agentId,
                                      @RequestParam(defaultValue = "24") int horizonHours) {
        access.assertCanViewAgent(principal, agentService.require(agentId));
        return predictionService.predict(agentId, horizonHours);
    }
}
