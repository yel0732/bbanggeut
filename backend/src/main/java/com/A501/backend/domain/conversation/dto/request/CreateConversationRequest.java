package com.A501.backend.domain.conversation.dto.request;

import java.util.UUID;

import com.A501.backend.domain.conversation.entity.ContextType;
import com.A501.backend.domain.conversation.entity.ConversationGoal;

import jakarta.validation.constraints.NotNull;

public record CreateConversationRequest(
        @NotNull ContextType contextType,
        UUID gameSessionId,
        @NotNull ConversationGoal conversationGoal,
        @NotNull Integer maxQuestionCount) {
}
