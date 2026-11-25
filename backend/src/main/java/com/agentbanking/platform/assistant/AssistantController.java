package com.agentbanking.platform.assistant;

import com.agentbanking.platform.assistant.AssistantChatService.AssistantChatResponse;
import com.agentbanking.platform.assistant.AssistantDtos.ChatRequest;
import com.agentbanking.platform.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assistant")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('AGENT','AREA_MANAGER','RISK_OFFICER','FINANCE_OFFICER','ADMIN')")
@Tag(name = "Assistant", description = "Explainable AI assistant with RBAC-scoped tool access")
public class AssistantController {

    private final AssistantChatService assistantChatService;

    @PostMapping("/chat")
    @Operation(summary = "Chat with the platform assistant (user Bearer token is forwarded for tool calls)")
    public AssistantChatResponse chat(@AuthenticationPrincipal UserPrincipal principal,
                                      @RequestHeader("Authorization") String authorization,
                                      @Valid @RequestBody ChatRequest request) {
        return assistantChatService.chat(principal, authorization, request);
    }
}