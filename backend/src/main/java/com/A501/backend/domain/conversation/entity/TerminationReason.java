package com.A501.backend.domain.conversation.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TerminationReason {

    USER_FINISHED(ConversationStatus.COMPLETED),
    MAX_QUESTION_REACHED(ConversationStatus.COMPLETED),
    CHILD_REQUESTED_STOP(ConversationStatus.COMPLETED),
    INACTIVITY_TIMEOUT(ConversationStatus.EXPIRED),
    CONNECTION_LOST(ConversationStatus.ABORTED),
    PAGE_CLOSED(ConversationStatus.ABORTED),
    SYSTEM_ERROR(ConversationStatus.FAILED);

    private final ConversationStatus targetStatus;

    public static TerminationReason from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("종료 사유가 필요합니다.");
        }

        try {
            return valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("허용되지 않은 종료 사유입니다.", exception);
        }
    }
}
