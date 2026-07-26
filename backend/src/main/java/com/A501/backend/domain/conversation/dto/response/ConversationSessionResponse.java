package com.A501.backend.domain.conversation.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

import com.A501.backend.common.util.ApiDateTimeMapper;
import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.ConversationStatus;
import com.A501.backend.domain.conversation.entity.ConversationTopic;
import com.A501.backend.domain.conversation.entity.ConversationTopicState;
import com.A501.backend.domain.conversation.entity.TerminationReason;

public record ConversationSessionResponse(
        String conversationId,
        Long childId,
        ConversationStatus status,
        ConversationTopic currentTopic,
        int totalQuestionCount,
        List<ConversationTopicResponse> topics,
        int currentSessionEpoch,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        TerminationReason terminationReason) {

    public ConversationSessionResponse {
        topics = List.copyOf(topics);
    }

    public static ConversationSessionResponse from(
            ConversationSession session,
            List<ConversationTopicState> topics) {
        return new ConversationSessionResponse(
                session.getConversationId(),
                session.getChildId(),
                session.getStatus(),
                session.getCurrentTopic(),
                session.getQuestionCount(),
                topics.stream()
                        .map(ConversationTopicResponse::from)
                        .toList(),
                session.getCurrentSessionEpoch(),
                ApiDateTimeMapper.utcToKst(session.getStartedAt()),
                ApiDateTimeMapper.utcToKst(session.getCompletedAt()),
                session.getTerminationReason());
    }

    public static ConversationSessionResponse from(ConversationSession session) {
        return from(session, List.of());
    }
}
