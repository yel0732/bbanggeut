package com.A501.backend.domain.conversation.entity;

/**
 * 대화 세션 상태. 허용 전이 규칙은 DIARY-1-T03에서 다룬다.
 */
public enum ConversationStatus {
    READY,
    IN_PROGRESS,
    COMPLETED,
    ABORTED,
    EXPIRED,
    FAILED
}
