package com.A501.backend.domain.conversation.dto.internal;

import com.A501.backend.domain.conversation.entity.TerminationReason;

public record DialogueAiResponse(
        DialogueSlotsPayload updatedSlots,
        DialogueAiDecision decision,
        TerminationReason terminationReason,
        String assistantMessage,
        String assistantQuestion,
        String questionGenerationStatus,
        String questionGenerationSource,
        String questionGenerationFailureCode) {

    public DialogueAiResponse {
        if (updatedSlots == null || decision == null) {
            throw new IllegalArgumentException("AI 대화 응답 필드가 누락되었습니다.");
        }
        if (decision == DialogueAiDecision.TERMINATE_SESSION) {
            if (terminationReason == null
                    || assistantMessage != null
                    || assistantQuestion != null) {
                throw new IllegalArgumentException("AI 종료 응답이 올바르지 않습니다.");
            }
        } else if ((decision == DialogueAiDecision.CONTINUE_TOPIC
                        && terminationReason != null)
                || (decision == DialogueAiDecision.SWITCH_TOPIC
                        && terminationReason == null)
                || assistantMessage == null
                || assistantMessage.isBlank()
                || assistantQuestion == null
                || assistantQuestion.isBlank()) {
            throw new IllegalArgumentException("AI 계속 응답이 올바르지 않습니다.");
        }
    }

    public DialogueAiResponse(
            DialogueSlotsPayload updatedSlots,
            DialogueAiDecision decision,
            TerminationReason terminationReason,
            String assistantQuestion,
            String questionGenerationStatus,
            String questionGenerationSource,
            String questionGenerationFailureCode) {
        this(
                updatedSlots,
                decision,
                terminationReason,
                assistantQuestion,
                assistantQuestion,
                questionGenerationStatus,
                questionGenerationSource,
                questionGenerationFailureCode);
    }
}
