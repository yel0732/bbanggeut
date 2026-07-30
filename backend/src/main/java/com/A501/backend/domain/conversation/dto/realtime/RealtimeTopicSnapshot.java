package com.A501.backend.domain.conversation.dto.realtime;

import java.util.List;

import com.A501.backend.domain.conversation.dto.internal.DialogueGameContextPayload;
import com.A501.backend.domain.conversation.dto.internal.DialogueSlotsPayload;
import com.A501.backend.domain.conversation.dto.internal.DialogueTopicHistoryPayload;
import com.A501.backend.domain.conversation.entity.ConversationTopic;

public record RealtimeTopicSnapshot(
        ConversationTopic type,
        int order,
        int maxQuestionCount,
        int questionCount,
        List<DialogueGameContextPayload> games,
        List<DialogueTopicHistoryPayload> topicHistory,
        String currentAssistantMessage,
        String currentQuestion,
        String currentTarget,
        List<String> skippedTargets,
        DialogueSlotsPayload initialSlots) {

    public RealtimeTopicSnapshot {
        games = games == null ? List.of() : List.copyOf(games);
        topicHistory = topicHistory == null ? List.of() : List.copyOf(topicHistory);
        skippedTargets = skippedTargets == null ? List.of() : List.copyOf(skippedTargets);
    }

    public RealtimeTopicSnapshot(
            ConversationTopic type,
            int order,
            int maxQuestionCount,
            int questionCount,
            List<DialogueGameContextPayload> games,
            List<DialogueTopicHistoryPayload> topicHistory,
            String currentAssistantMessage,
            String currentQuestion,
            String currentTarget,
            DialogueSlotsPayload initialSlots) {
        this(
                type,
                order,
                maxQuestionCount,
                questionCount,
                games,
                topicHistory,
                currentAssistantMessage,
                currentQuestion,
                currentTarget,
                List.of(),
                initialSlots);
    }

    public RealtimeTopicSnapshot(
            ConversationTopic type,
            int order,
            int maxQuestionCount,
            int questionCount,
            List<DialogueGameContextPayload> games,
            List<DialogueTopicHistoryPayload> topicHistory,
            String currentAssistantMessage,
            String currentQuestion,
            DialogueSlotsPayload initialSlots) {
        this(
                type,
                order,
                maxQuestionCount,
                questionCount,
                games,
                topicHistory,
                currentAssistantMessage,
                currentQuestion,
                null,
                initialSlots);
    }

    public RealtimeTopicSnapshot(
            ConversationTopic type,
            int order,
            int maxQuestionCount,
            int questionCount,
            List<DialogueGameContextPayload> games,
            DialogueSlotsPayload initialSlots) {
        this(
                type,
                order,
                maxQuestionCount,
                questionCount,
                games,
                List.of(),
                null,
                null,
                null,
                initialSlots);
    }

    public RealtimeTopicSnapshot(
            ConversationTopic type,
            int order,
            int maxQuestionCount,
            List<DialogueGameContextPayload> games,
            DialogueSlotsPayload initialSlots) {
        this(type, order, maxQuestionCount, 0, games, List.of(), null, null, null, initialSlots);
    }

    public static RealtimeTopicSnapshot withoutGames(
            ConversationTopic type,
            int order,
            int maxQuestionCount) {
        return new RealtimeTopicSnapshot(
                type,
                order,
                maxQuestionCount,
                0,
                List.of(),
                List.of(),
                null,
                null,
                null,
                DialogueSlotsPayload.empty());
    }
}
