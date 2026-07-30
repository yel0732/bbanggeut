package com.A501.backend.domain.conversation.dto.internal;

import java.util.List;

import com.A501.backend.domain.conversation.entity.TerminationReason;

public record DialogueAiResponse(
        DialogueSlotsPayload updatedSlots,
        List<String> updatedSkippedTargets,
        DialogueAiDecision decision,
        TerminationReason terminationReason,
        String assistantMessage,
        String assistantQuestion,
        String assistantTarget,
        String questionGenerationStatus,
        String questionGenerationSource,
        String questionGenerationFailureCode,
        String closingMessage) {

    public DialogueAiResponse {
        if (updatedSlots == null || decision == null) {
            throw new IllegalArgumentException("AI 대화 응답 필드가 누락되었습니다.");
        }
        if (updatedSkippedTargets != null) {
            updatedSkippedTargets = List.copyOf(updatedSkippedTargets);
        }
        if (decision == DialogueAiDecision.TERMINATE_SESSION) {
            if (terminationReason == null
                    || assistantMessage != null
                    || assistantQuestion != null
                    || assistantTarget != null) {
                throw new IllegalArgumentException("AI 종료 응답이 올바르지 않습니다.");
            }
        } else if ((decision == DialogueAiDecision.CONTINUE_TOPIC
                        && terminationReason != null)
                || (decision == DialogueAiDecision.SWITCH_TOPIC
                        && terminationReason == null)
                || assistantMessage == null
                || assistantMessage.isBlank()
                || assistantQuestion == null
                || assistantQuestion.isBlank()
                || closingMessage != null) {
            throw new IllegalArgumentException("AI 계속 응답이 올바르지 않습니다.");
        }
    }

    public DialogueAiResponse(
            DialogueSlotsPayload updatedSlots,
            List<String> updatedSkippedTargets,
            DialogueAiDecision decision,
            TerminationReason terminationReason,
            String assistantMessage,
            String assistantQuestion,
            String assistantTarget,
            String questionGenerationStatus,
            String questionGenerationSource,
            String questionGenerationFailureCode) {
        this(
                updatedSlots,
                updatedSkippedTargets,
                decision,
                terminationReason,
                assistantMessage,
                assistantQuestion,
                assistantTarget,
                questionGenerationStatus,
                questionGenerationSource,
                questionGenerationFailureCode,
                null);
    }

    public DialogueAiResponse(
            DialogueSlotsPayload updatedSlots,
            DialogueAiDecision decision,
            TerminationReason terminationReason,
            String assistantMessage,
            String assistantQuestion,
            String assistantTarget,
            String questionGenerationStatus,
            String questionGenerationSource,
            String questionGenerationFailureCode) {
        this(
                updatedSlots,
                null,
                decision,
                terminationReason,
                assistantMessage,
                assistantQuestion,
                assistantTarget,
                questionGenerationStatus,
                questionGenerationSource,
                questionGenerationFailureCode,
                null);
    }

    public DialogueAiResponse(
            DialogueSlotsPayload updatedSlots,
            DialogueAiDecision decision,
            TerminationReason terminationReason,
            String assistantMessage,
            String assistantQuestion,
            String questionGenerationStatus,
            String questionGenerationSource,
            String questionGenerationFailureCode) {
        this(
                updatedSlots,
                decision,
                terminationReason,
                assistantMessage,
                assistantQuestion,
                null,
                questionGenerationStatus,
                questionGenerationSource,
                questionGenerationFailureCode);
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
                null,
                questionGenerationStatus,
                questionGenerationSource,
                questionGenerationFailureCode);
    }
}
