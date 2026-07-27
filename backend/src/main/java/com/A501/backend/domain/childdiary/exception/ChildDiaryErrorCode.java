package com.A501.backend.domain.childdiary.exception;

import org.springframework.http.HttpStatus;

import com.A501.backend.common.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChildDiaryErrorCode implements ErrorCode {
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "CHILD_DIARY_UNAUTHORIZED", "인증이 필요합니다."),
    CHILD_NOT_FOUND(HttpStatus.NOT_FOUND, "CHILD_DIARY_CHILD_NOT_FOUND", "아이를 찾을 수 없습니다."),
    CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "CHILD_DIARY_CONVERSATION_NOT_FOUND", "대화를 찾을 수 없습니다."),
    CONVERSATION_NOT_COMPLETED(HttpStatus.CONFLICT, "CHILD_DIARY_CONVERSATION_NOT_COMPLETED", "완료된 대화만 일기로 만들 수 있습니다."),
    SOURCE_EMPTY(HttpStatus.UNPROCESSABLE_ENTITY, "CHILD_DIARY_SOURCE_EMPTY", "일기로 만들 대화 내용이 없습니다."),
    PREVIEW_INVALID(HttpStatus.BAD_REQUEST, "CHILD_DIARY_PREVIEW_INVALID", "일기 미리보기가 유효하지 않거나 만료되었습니다."),
    ALREADY_SAVED(HttpStatus.CONFLICT, "CHILD_DIARY_ALREADY_SAVED", "이 대화의 일기가 이미 저장되었습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "CHILD_DIARY_NOT_FOUND", "일기를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
