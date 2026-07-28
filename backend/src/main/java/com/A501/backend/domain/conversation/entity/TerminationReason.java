package com.A501.backend.domain.conversation.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TerminationReason {

    USER_FINISHED(ConversationStatus.COMPLETED),
    MAX_QUESTION_REACHED(ConversationStatus.COMPLETED),
    CHILD_REQUESTED_STOP(ConversationStatus.COMPLETED),
    REQUIRED_SLOTS_FILLED(ConversationStatus.COMPLETED),
    // 아이가 일기로 남기지 않고 대화를 명시적으로 그만둔 경우. 완료(COMPLETED)와 달리
    // 이탈로 취급해 대화 원문을 정리한다.
    USER_ABANDONED(ConversationStatus.ABORTED),
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
