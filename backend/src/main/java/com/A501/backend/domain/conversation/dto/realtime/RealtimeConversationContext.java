package com.A501.backend.domain.conversation.dto.realtime;

import com.A501.backend.domain.conversation.entity.ConversationGoal;
import com.A501.backend.domain.conversation.entity.ConversationTopic;

public record RealtimeConversationContext(
        ConversationGoal goal,
        String language,
        ConversationTopic currentTopic,
        int totalMaxQuestionCount) {
}
