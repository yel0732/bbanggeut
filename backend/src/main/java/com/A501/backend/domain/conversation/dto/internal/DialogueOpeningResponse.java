package com.A501.backend.domain.conversation.dto.internal;

public record DialogueOpeningResponse(
        String assistantMessage,
        String assistantQuestion,
        String assistantTarget) {

    public DialogueOpeningResponse(
            String assistantMessage,
            String assistantQuestion) {
        this(assistantMessage, assistantQuestion, null);
    }
}
