package com.agentbanking.platform.assistant;

import com.agentbanking.platform.ai.AiServiceClient;
import com.agentbanking.platform.assistant.AssistantDtos.ChatMessage;
import com.agentbanking.platform.assistant.AssistantDtos.ChatRequest;
import com.agentbanking.platform.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AssistantChatService {

    private final AiServiceClient aiClient;

    public AssistantChatResponse chat(UserPrincipal principal, String authorization, ChatRequest request) {
        List<AssistantMessage> conversation = request.conversation() == null ? List.of()
                : request.conversation().stream().map(m -> new AssistantMessage(m.role(), m.content())).toList();
        AssistantAiRequest body = new AssistantAiRequest(principal.getId(), principal.getUsername(), principal.getRole().name(),
                principal.getAgentId(), principal.getAreaId(), request.question(), conversation, authorization);
        return aiClient.postAssistant("/internal/ai/v1/assistant/chat", body, AssistantChatResponse.class);
    }

    public record AssistantMessage(String role, String content) {
    }

    public record AssistantAiRequest(UUID userId, String username, String role, UUID agentId, UUID areaId, String question,
                                     List<AssistantMessage> conversation, String authorization) {
    }

    public record AssistantChatResponse(String interactionId, String answer, boolean refused, String engine,
                                        List<Object> toolsUsed, List<String> dataClassifications, String disclaimer) {
    }
}