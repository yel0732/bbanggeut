package com.A501.backend.domain.conversation.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ConversationErrorCode {

    CHILD_NOT_FOUND(HttpStatus.NOT_FOUND, "아이 프로필을 찾을 수 없습니다."),
    CHILD_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 아이 프로필에 접근할 수 없습니다."),
    CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "대화 세션을 찾을 수 없습니다."),
    CONVERSATION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 대화 세션에 접근할 수 없습니다."),
    ACTIVE_CONVERSATION_EXISTS(HttpStatus.CONFLICT, "이미 진행 중인 대화 세션이 있습니다."),
    INVALID_MAX_QUESTION_COUNT(HttpStatus.BAD_REQUEST, "최대 질문 수는 5개 이상 6개 이하여야 합니다."),
    GAME_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "게임 세션을 찾을 수 없습니다."),
    INVALID_CONVERSATION_CONTEXT(HttpStatus.BAD_REQUEST, "대화 맥락과 게임 세션 정보가 올바르지 않습니다."),
    INVALID_TERMINATION_REASON(HttpStatus.BAD_REQUEST, "종료 사유가 올바르지 않습니다."),
    INVALID_SESSION_STATE(HttpStatus.CONFLICT, "현재 대화 세션 상태에서는 요청을 처리할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
