package com.A501.backend.domain.conversation.dto.response;

import java.time.OffsetDateTime;

import com.A501.backend.common.util.ApiDateTimeMapper;
import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.ConversationStatus;
import com.A501.backend.domain.conversation.entity.TerminationReason;

public record CompleteConversationResponse(
        String conversationId,
        ConversationStatus status,
        int questionCount,
        TerminationReason terminationReason,
        OffsetDateTime completedAt) {

    public static CompleteConversationResponse from(ConversationSession session) {
        return new CompleteConversationResponse(
                session.getConversationId(),
                session.getStatus(),
                session.getQuestionCount(),
                session.getTerminationReason(),
                ApiDateTimeMapper.utcToKst(session.getCompletedAt()));
    }
}
