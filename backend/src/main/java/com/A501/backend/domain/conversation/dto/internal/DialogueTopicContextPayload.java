package com.A501.backend.domain.conversation.dto.internal;

import java.util.List;

import com.A501.backend.domain.conversation.entity.ConversationTopic;

public record DialogueTopicContextPayload(
        ConversationTopic topic,
        List<DialogueGameContextPayload> games) {

    public DialogueTopicContextPayload {
        games = games == null ? List.of() : List.copyOf(games);
    }

    public static DialogueTopicContextPayload empty(ConversationTopic topic) {
        return new DialogueTopicContextPayload(topic, List.of());
    }
}
