package com.A501.backend.domain.conversation.dto.internal;

public record DialogueTopicHistoryPayload(
        String assistantMessage,
        String assistantQuestion,
        String childAnswer) {

    public DialogueTopicHistoryPayload {
        if (assistantMessage == null || assistantMessage.isBlank()) {
            throw new IllegalArgumentException("AI 메시지는 필수입니다.");
        }
        if (assistantQuestion == null || assistantQuestion.isBlank()) {
            throw new IllegalArgumentException("AI 질문은 필수입니다.");
        }
        if (childAnswer == null || childAnswer.isBlank()) {
            throw new IllegalArgumentException("아이 답변은 필수입니다.");
        }
    }
}
