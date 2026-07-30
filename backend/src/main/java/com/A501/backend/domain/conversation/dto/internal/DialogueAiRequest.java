package com.A501.backend.domain.conversation.dto.internal;

import java.util.List;

import com.A501.backend.domain.conversation.entity.ConversationTopic;

public record DialogueAiRequest(
        String requestId,
        String conversationId,
        String childAnswer,
        String currentQuestion,
        String currentTarget,
        List<String> skippedTargets,
        ConversationTopic currentTopic,
        ConversationTopic nextTopic,
        DialogueSlotsPayload currentTopicSlots,
        List<String> recentQuestions,
        List<DialogueTopicHistoryPayload> topicHistory,
        DialogueTopicContextPayload topicContext,
        int topicQuestionCount,
        int topicMaxQuestionCount) {

    public DialogueAiRequest {
        recentQuestions = recentQuestions == null
                ? List.of()
                : List.copyOf(recentQuestions);
        topicHistory = topicHistory == null
                ? List.of()
                : List.copyOf(topicHistory);
        skippedTargets = skippedTargets == null
                ? List.of()
                : List.copyOf(skippedTargets);
    }

    public DialogueAiRequest(
            String requestId,
            String conversationId,
            String childAnswer,
            String currentQuestion,
            String currentTarget,
            ConversationTopic currentTopic,
            ConversationTopic nextTopic,
            DialogueSlotsPayload currentTopicSlots,
            List<String> recentQuestions,
            List<DialogueTopicHistoryPayload> topicHistory,
            DialogueTopicContextPayload topicContext,
            int topicQuestionCount,
            int topicMaxQuestionCount) {
        this(
                requestId,
                conversationId,
                childAnswer,
                currentQuestion,
                currentTarget,
                List.of(),
                currentTopic,
                nextTopic,
                currentTopicSlots,
                recentQuestions,
                topicHistory,
                topicContext,
                topicQuestionCount,
                topicMaxQuestionCount);
    }

    public DialogueAiRequest(
            String requestId,
            String conversationId,
            String childAnswer,
            String currentQuestion,
            ConversationTopic currentTopic,
            ConversationTopic nextTopic,
            DialogueSlotsPayload currentTopicSlots,
            List<String> recentQuestions,
            List<DialogueTopicHistoryPayload> topicHistory,
            DialogueTopicContextPayload topicContext,
            int topicQuestionCount,
            int topicMaxQuestionCount) {
        this(
                requestId,
                conversationId,
                childAnswer,
                currentQuestion,
                null,
                currentTopic,
                nextTopic,
                currentTopicSlots,
                recentQuestions,
                topicHistory,
                topicContext,
                topicQuestionCount,
                topicMaxQuestionCount);
    }

    public DialogueAiRequest(
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
        this(
                requestId,
                conversationId,
                childAnswer,
                currentQuestion,
                null,
                currentTopic,
                nextTopic,
                currentTopicSlots,
                recentQuestions,
                List.of(),
                topicContext,
                topicQuestionCount,
                topicMaxQuestionCount);
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
                null,
                ConversationTopic.DAILY,
                null,
                currentSlots,
                recentQuestions,
                List.of(),
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
