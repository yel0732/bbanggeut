package com.A501.backend.domain.conversation.dto.response;

import com.A501.backend.domain.conversation.entity.ConversationTopic;
import com.A501.backend.domain.conversation.entity.ConversationTopicState;
import com.A501.backend.domain.conversation.entity.ConversationTopicStatus;
import com.A501.backend.domain.conversation.entity.TerminationReason;

public record ConversationTopicResponse(
        ConversationTopic type,
        int order,
        ConversationTopicStatus status,
        int questionCount,
        int maxQuestionCount,
        TerminationReason completionReason) {

    public static ConversationTopicResponse from(ConversationTopicState topic) {
        return new ConversationTopicResponse(
                topic.getTopicType(),
                topic.getTopicOrder(),
                topic.getStatus(),
                topic.getQuestionCount(),
                topic.getMaxQuestionCount(),
                topic.getCompletionReason());
    }
}
