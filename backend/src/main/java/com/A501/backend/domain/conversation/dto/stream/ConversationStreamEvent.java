package com.A501.backend.domain.conversation.dto.stream;

import java.time.OffsetDateTime;

import tools.jackson.databind.JsonNode;

/**
 * Redis Stream {@code conversation-events}의 공통 Envelope
 * (REALTIME_EVENT_CONTRACT.md §7.1). FastAPI Producer와 문자 그대로 같은 JSON을
 * 공유하며, {@code payload}는 이벤트 종류별 모양이 달라 {@link JsonNode}로 받아
 * W06 Consumer가 Handler별로 역직렬화한다.
 *
 * <p>{@code turnId}/{@code turnSeq}/{@code responseId}는 이벤트에 따라 null일 수 있다
 * (계약 §7.1은 이 필드들을 명시적 null로 담는다).
 */
public record ConversationStreamEvent(
        String eventId,
        ConversationStreamEventType eventType,
        String conversationId,
        String realtimeSessionId,
        int sessionEpoch,
        String turnId,
        Integer turnSeq,
        String responseId,
        OffsetDateTime occurredAt,
        JsonNode payload) {
}
