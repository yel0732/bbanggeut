package com.A501.backend.domain.conversation.dto.stream;

import tools.jackson.databind.JsonNode;

public record AiTurnGeneratedPayload(
        String text,
        String questionText,
        String targetSlot,
        String topicType,
        String slotsTopicType,
        JsonNode topicSlots,
        Integer topicQuestionCount,
        Integer sessionQuestionCount,
        String topicCompletionReason) {
}
