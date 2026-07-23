package com.A501.backend.domain.conversation.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.A501.backend.common.util.ApiDateTimeMapper;
import com.A501.backend.domain.conversation.entity.ContextType;
import com.A501.backend.domain.conversation.entity.ConversationGoal;
import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.ConversationStatus;

public record CreateConversationResponse(
        String conversationId,
        Long childId,
        UUID gameSessionId,
        ConversationStatus status,
        ContextType contextType,
        ConversationGoal conversationGoal,
        int questionCount,
        int maxQuestionCount,
        OffsetDateTime createdAt) {

    public static CreateConversationResponse from(ConversationSession session) {
        return new CreateConversationResponse(
                session.getConversationId(),
                session.getChildId(),
                session.getGameSessionId(),
                session.getStatus(),
                session.getContextType(),
                session.getConversationGoal(),
                session.getQuestionCount(),
                session.getMaxQuestionCount(),
                ApiDateTimeMapper.utcToKst(session.getCreatedAt()));
    }
}
