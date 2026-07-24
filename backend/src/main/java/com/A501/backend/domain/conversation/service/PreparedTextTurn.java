package com.A501.backend.domain.conversation.service;

import java.util.List;

import com.A501.backend.domain.conversation.dto.internal.DialogueSlotsPayload;

public record PreparedTextTurn(
        String conversationId,
        Long sessionVersion,
        DialogueSlotsPayload currentSlots,
        String currentQuestion,
        List<String> recentQuestions,
        int questionCount,
        int maxQuestionCount) {

    public PreparedTextTurn {
        recentQuestions = List.copyOf(recentQuestions);
    }
}
