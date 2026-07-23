package com.A501.backend.domain.conversation.dto.response;

import java.time.OffsetDateTime;

import com.A501.backend.common.util.ApiDateTimeMapper;
import com.A501.backend.domain.conversation.entity.ContextType;
import com.A501.backend.domain.conversation.entity.ConversationGoal;
import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.ConversationStatus;

public record ConversationSessionResponse(
        String conversationId,
        Long childId,
        ConversationStatus status,
        ContextType contextType,
        ConversationGoal conversationGoal,
        int questionCount,
        int maxQuestionCount,
        int currentSessionEpoch,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        String terminationReason) {

    public static ConversationSessionResponse from(ConversationSession session) {
        return new ConversationSessionResponse(
                session.getConversationId(),
                session.getChildId(),
                session.getStatus(),
                session.getContextType(),
                session.getConversationGoal(),
                session.getQuestionCount(),
                session.getMaxQuestionCount(),
                session.getCurrentSessionEpoch(),
                ApiDateTimeMapper.utcToKst(session.getStartedAt()),
                ApiDateTimeMapper.utcToKst(session.getCompletedAt()),
                session.getTerminationReason());
    }
}
