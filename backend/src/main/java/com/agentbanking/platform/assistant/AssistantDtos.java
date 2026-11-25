package com.agentbanking.platform.assistant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class AssistantDtos {

    private AssistantDtos() {
    }

    public record ChatRequest(@NotBlank @Size(max = 4000) String question,
                              @Valid @Size(max = 20) List<ChatMessage> conversation) {
    }

    public record ChatMessage(@NotBlank String role, @NotBlank @Size(max = 8000) String content) {
    }
}