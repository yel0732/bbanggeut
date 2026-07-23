package com.A501.backend.domain.auth.exception;

import org.springframework.http.HttpStatus;

import com.A501.backend.common.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "AUTH_EMAIL_ALREADY_EXISTS", "이미 사용 중인 이메일입니다."),
    UNDER_AGE(HttpStatus.BAD_REQUEST, "AUTH_UNDER_AGE", "만 19세 이상만 가입할 수 있습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
