package com.A501.backend.domain.conversation.dto.internal;

import java.util.List;

import com.A501.backend.domain.conversation.entity.ConversationTopic;

public record DialogueAiRequest(
        String requestId,
        String conversationId,
        String childAnswer,
        String currentQuestion,
        ConversationTopic currentTopic,
        ConversationTopic nextTopic,
        DialogueSlotsPayload currentTopicSlots,
        List<String> recentQuestions,
        DialogueTopicContextPayload topicContext,
        int topicQuestionCount,
        int topicMaxQuestionCount) {

    public DialogueAiRequest {
        recentQuestions = recentQuestions == null
                ? List.of()
                : List.copyOf(recentQuestions);
    }

    public DialogueAiRequest(
            String requestId,
            String conversationId,
            String childAnswer,
            String currentQuestion,
            DialogueSlotsPayload currentSlots,
            List<String> recentQuestions,
            Object gameContext,
            int questionCount,
            int maxQuestionCount) {
        this(
                requestId,
                conversationId,
                childAnswer,
                currentQuestion,
                ConversationTopic.DAILY,
                null,
                currentSlots,
                recentQuestions,
                DialogueTopicContextPayload.empty(ConversationTopic.DAILY),
                questionCount,
                maxQuestionCount);
    }

    @Deprecated
    public DialogueSlotsPayload currentSlots() {
        return currentTopicSlots;
    }

    @Deprecated
    public int questionCount() {
        return topicQuestionCount;
    }

    @Deprecated
    public int maxQuestionCount() {
        return topicMaxQuestionCount;
    }
}
