package com.A501.backend.domain.conversation.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

import com.A501.backend.common.util.ApiDateTimeMapper;
import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.ConversationStatus;
import com.A501.backend.domain.conversation.entity.ConversationTopic;
import com.A501.backend.domain.conversation.entity.ConversationTopicState;

public record CreateConversationResponse(
        String conversationId,
        Long childId,
        ConversationStatus status,
        ConversationTopic currentTopic,
        int totalQuestionCount,
        List<ConversationTopicResponse> topics,
        OffsetDateTime createdAt) {

    public CreateConversationResponse {
        topics = List.copyOf(topics);
    }

    public static CreateConversationResponse from(
            ConversationSession session,
            List<ConversationTopicState> topics) {
        return new CreateConversationResponse(
                session.getConversationId(),
                session.getChildId(),
                session.getStatus(),
                session.getCurrentTopic(),
                session.getQuestionCount(),
                topics.stream()
                        .map(ConversationTopicResponse::from)
                        .toList(),
                ApiDateTimeMapper.utcToKst(session.getCreatedAt()));
    }

    public static CreateConversationResponse from(ConversationSession session) {
        return from(session, List.of());
    }
}
