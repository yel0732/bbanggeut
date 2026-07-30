package com.A501.backend.domain.conversation.dto.internal;

import com.A501.backend.domain.conversation.entity.ConversationTopic;

public record DialogueOpeningRequest(
        ConversationTopic currentTopic,
        DialogueTopicContextPayload topicContext) {
}
