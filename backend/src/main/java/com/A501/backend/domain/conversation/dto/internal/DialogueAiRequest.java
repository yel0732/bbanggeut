package com.A501.backend.domain.conversation.dto.internal;

import java.util.List;

public record DialogueAiRequest(
        String requestId,
        String conversationId,
        String childAnswer,
        String currentQuestion,
        DialogueSlotsPayload currentSlots,
        List<String> recentQuestions,
        Object gameContext,
        int questionCount,
        int maxQuestionCount) {

    public DialogueAiRequest {
        recentQuestions = recentQuestions == null
                ? List.of()
                : List.copyOf(recentQuestions);
    }
}
