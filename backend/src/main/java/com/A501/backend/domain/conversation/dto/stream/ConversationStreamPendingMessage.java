package com.A501.backend.domain.conversation.dto.stream;

public record ConversationStreamPendingMessage(
        String recordId,
        long deliveryCount) {

    public ConversationStreamPendingMessage {
        if (recordId == null || recordId.isBlank()) {
            throw new IllegalArgumentException("Redis Stream record ID가 필요합니다.");
        }
        if (deliveryCount < 1) {
            throw new IllegalArgumentException("Pending 전달 횟수는 1 이상이어야 합니다.");
        }
    }
}
