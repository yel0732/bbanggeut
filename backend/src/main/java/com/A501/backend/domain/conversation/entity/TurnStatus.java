package com.A501.backend.domain.conversation.entity;

/**
 * 대화 턴 상태. 허용 전이 규칙은 DIARY-1-T03에서 다룬다.
 */
public enum TurnStatus {
    GENERATING,
    COMPLETED,
    INTERRUPTED,
    FAILED
}
