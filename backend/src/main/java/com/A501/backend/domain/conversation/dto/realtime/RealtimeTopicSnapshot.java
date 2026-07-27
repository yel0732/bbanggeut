package com.A501.backend.domain.conversation.dto.realtime;

import java.util.List;

import com.A501.backend.domain.conversation.dto.internal.DialogueGameContextPayload;
import com.A501.backend.domain.conversation.dto.internal.DialogueSlotsPayload;
import com.A501.backend.domain.conversation.entity.ConversationTopic;

public record RealtimeTopicSnapshot(
        ConversationTopic type,
        int order,
        int maxQuestionCount,
        List<DialogueGameContextPayload> games,
        DialogueSlotsPayload initialSlots) {

    public RealtimeTopicSnapshot {
        games = games == null ? List.of() : List.copyOf(games);
    }

    public static RealtimeTopicSnapshot withoutGames(
            ConversationTopic type,
            int order,
            int maxQuestionCount) {
        return new RealtimeTopicSnapshot(
                type,
                order,
                maxQuestionCount,
                List.of(),
                DialogueSlotsPayload.empty());
    }
}
