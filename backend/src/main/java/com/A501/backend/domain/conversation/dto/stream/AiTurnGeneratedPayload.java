package com.A501.backend.domain.conversation.dto.stream;

import java.util.List;

import tools.jackson.databind.JsonNode;

public record AiTurnGeneratedPayload(
        String text,
        String questionText,
        String targetSlot,
        String topicType,
        String slotsTopicType,
        JsonNode topicSlots,
        List<String> skippedTargets,
        Integer topicQuestionCount,
        Integer sessionQuestionCount,
        String topicCompletionReason) {
}
