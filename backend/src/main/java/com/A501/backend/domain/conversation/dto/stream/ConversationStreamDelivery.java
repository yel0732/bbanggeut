package com.A501.backend.domain.conversation.dto.stream;

public record ConversationStreamDelivery(
        String recordId,
        String eventJson,
        long deliveryCount) {

    public ConversationStreamDelivery {
        if (recordId == null || recordId.isBlank()) {
            throw new IllegalArgumentException("Redis Stream record ID가 필요합니다.");
        }
        if (eventJson == null || eventJson.isBlank()) {
            throw new IllegalArgumentException("Conversation Event JSON이 필요합니다.");
        }
        if (deliveryCount < 1) {
            throw new IllegalArgumentException("Event 전달 횟수는 1 이상이어야 합니다.");
        }
    }
}
