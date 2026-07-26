package com.A501.backend.domain.conversation.service;

import java.util.List;

import com.A501.backend.domain.conversation.dto.internal.DialogueSlotsPayload;
import com.A501.backend.domain.conversation.dto.internal.DialogueTopicContextPayload;
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
        List<String> recentQuestions,
        int topicQuestionCount,
        int topicMaxQuestionCount) {

    public PreparedTextTurn {
        recentQuestions = List.copyOf(recentQuestions);
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
                recentQuestions,
                questionCount,
                maxQuestionCount);
    }
}
