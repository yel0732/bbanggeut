package com.A501.backend.domain.child.exception;

import org.springframework.http.HttpStatus;

import com.A501.backend.common.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChildErrorCode implements ErrorCode {

    CHILD_NOT_FOUND(HttpStatus.NOT_FOUND, "CHILD_NOT_FOUND", "자녀 프로필을 찾을 수 없습니다."),
    CHILD_NAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "CHILD_NAME_ALREADY_EXISTS", "이미 사용 중인 닉네임입니다."),
    CHILD_LIMIT_EXCEEDED(HttpStatus.FORBIDDEN, "CHILD_LIMIT_EXCEEDED", "자녀 프로필은 최대 10명까지 등록할 수 있습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
