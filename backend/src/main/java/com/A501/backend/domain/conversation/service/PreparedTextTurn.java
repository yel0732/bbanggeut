package com.A501.backend.domain.conversation.service;

import java.util.List;

import com.A501.backend.domain.conversation.dto.internal.DialogueSlotsPayload;
import com.A501.backend.domain.conversation.dto.internal.DialogueTopicContextPayload;
import com.A501.backend.domain.conversation.dto.internal.DialogueTopicHistoryPayload;
import com.A501.backend.domain.conversation.entity.ConversationTopic;

public record PreparedTextTurn(
        String conversationId,
        Long sessionVersion,
        Long topicVersion,
        ConversationTopic currentTopic,
        ConversationTopic nextTopic,
        DialogueSlotsPayload currentTopicSlots,
        DialogueTopicContextPayload topicContext,
        String currentQuestion,
        String currentTarget,
        List<String> skippedTargets,
        List<String> recentQuestions,
        List<DialogueTopicHistoryPayload> topicHistory,
        int topicQuestionCount,
        int topicMaxQuestionCount) {

    public PreparedTextTurn {
        recentQuestions = List.copyOf(recentQuestions);
        topicHistory = topicHistory == null ? List.of() : List.copyOf(topicHistory);
        skippedTargets = skippedTargets == null ? List.of() : List.copyOf(skippedTargets);
    }

    public PreparedTextTurn(
            String conversationId,
            Long sessionVersion,
            Long topicVersion,
            ConversationTopic currentTopic,
            ConversationTopic nextTopic,
            DialogueSlotsPayload currentTopicSlots,
            DialogueTopicContextPayload topicContext,
            String currentQuestion,
            String currentTarget,
            List<String> recentQuestions,
            List<DialogueTopicHistoryPayload> topicHistory,
            int topicQuestionCount,
            int topicMaxQuestionCount) {
        this(
                conversationId,
                sessionVersion,
                topicVersion,
                currentTopic,
                nextTopic,
                currentTopicSlots,
                topicContext,
                currentQuestion,
                currentTarget,
                List.of(),
                recentQuestions,
                topicHistory,
                topicQuestionCount,
                topicMaxQuestionCount);
    }

    public PreparedTextTurn(
            String conversationId,
            Long sessionVersion,
            Long topicVersion,
            ConversationTopic currentTopic,
            ConversationTopic nextTopic,
            DialogueSlotsPayload currentTopicSlots,
            DialogueTopicContextPayload topicContext,
            String currentQuestion,
            List<String> recentQuestions,
            List<DialogueTopicHistoryPayload> topicHistory,
            int topicQuestionCount,
            int topicMaxQuestionCount) {
        this(
                conversationId,
                sessionVersion,
                topicVersion,
                currentTopic,
                nextTopic,
                currentTopicSlots,
                topicContext,
                currentQuestion,
                null,
                recentQuestions,
                topicHistory,
                topicQuestionCount,
                topicMaxQuestionCount);
    }

    public PreparedTextTurn(
            String conversationId,
            Long sessionVersion,
            Long topicVersion,
            ConversationTopic currentTopic,
            ConversationTopic nextTopic,
            DialogueSlotsPayload currentTopicSlots,
            DialogueTopicContextPayload topicContext,
            String currentQuestion,
            List<String> recentQuestions,
            int topicQuestionCount,
            int topicMaxQuestionCount) {
        this(
                conversationId,
                sessionVersion,
                topicVersion,
                currentTopic,
                nextTopic,
                currentTopicSlots,
                topicContext,
                currentQuestion,
                null,
                recentQuestions,
                List.of(),
                topicQuestionCount,
                topicMaxQuestionCount);
    }

    public PreparedTextTurn(
            String conversationId,
            Long sessionVersion,
            DialogueSlotsPayload currentSlots,
            String currentQuestion,
            List<String> recentQuestions,
            int questionCount,
            int maxQuestionCount) {
        this(
                conversationId,
                sessionVersion,
                0L,
                ConversationTopic.DAILY,
                null,
                currentSlots,
                DialogueTopicContextPayload.empty(ConversationTopic.DAILY),
                currentQuestion,
                null,
                recentQuestions,
                List.of(),
                questionCount,
                maxQuestionCount);
    }
}
