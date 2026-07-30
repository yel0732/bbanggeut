package com.A501.backend.domain.conversation.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TerminationReason {

    // 마지막 주제까지 대화를 마친 경우에만 COMPLETED다. 이 두 사유는 대화 엔진이
    // 마지막 주제를 끝냈을 때만 발생하며, 그 밖의 모든 종료는 중도 이탈로 취급해
    // 대화 원문을 정리한다(COMPLETED가 아니면 일기·리포트를 만들지 않는다).
    REQUIRED_SLOTS_FILLED(ConversationStatus.COMPLETED),
    MAX_QUESTION_REACHED(ConversationStatus.COMPLETED),
    // 아이·부모가 대화를 끝내는 조작. 완주가 아니므로 원문을 남기지 않는다.
    USER_FINISHED(ConversationStatus.ABORTED),
    USER_ABANDONED(ConversationStatus.ABORTED),
    CHILD_REQUESTED_STOP(ConversationStatus.ABORTED),
    CHILD_REQUESTED_TOPIC_SWITCH(ConversationStatus.ABORTED),
    // 연결이 끊기거나 응답이 없어 중단된 경우.
    CONNECTION_LOST(ConversationStatus.ABORTED),
    PAGE_CLOSED(ConversationStatus.ABORTED),
    INACTIVITY_TIMEOUT(ConversationStatus.EXPIRED),
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
