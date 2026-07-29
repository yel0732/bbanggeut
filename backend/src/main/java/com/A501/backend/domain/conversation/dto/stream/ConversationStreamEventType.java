package com.A501.backend.domain.conversation.dto.stream;

/**
 * Redis Stream {@code conversation-events}에 실리는 확정 이벤트 종류
 * (REALTIME_EVENT_CONTRACT.md §7.2). FastAPI가 생산하고 Spring(W06)이 소비한다.
 *
 * <p>{@code AI_TURN_INTERRUPTED}는 DIARY-4(Barge-in)에서, {@code SESSION_FAILED}는
 * 장애 복구 Task에서 실제 발행이 배선된다. enum에는 계약 완결을 위해 미리 포함한다.
 */
public enum ConversationStreamEventType {
    SESSION_STARTED,
    CHILD_TURN_FINALIZED,
    AI_TURN_GENERATED,
    AI_TURN_COMPLETED,
    AI_TURN_INTERRUPTED,
    SESSION_COMPLETED,
    SESSION_FAILED
}
