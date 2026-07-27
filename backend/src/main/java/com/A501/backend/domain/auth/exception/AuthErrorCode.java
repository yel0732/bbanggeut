package com.A501.backend.domain.auth.exception;

import org.springframework.http.HttpStatus;

import com.A501.backend.common.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "AUTH_EMAIL_ALREADY_EXISTS", "이미 사용 중인 이메일입니다."),
    UNDER_AGE(HttpStatus.BAD_REQUEST, "AUTH_UNDER_AGE", "만 19세 이상만 가입할 수 있습니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_REFRESH_TOKEN", "유효하지 않은 토큰입니다. 다시 로그인해주세요."),
    REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "AUTH_REFRESH_TOKEN_REUSED", "토큰 재사용이 감지되었습니다. 다시 로그인해주세요."),
    INVALID_GOOGLE_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_GOOGLE_TOKEN", "구글 인증에 실패했습니다. 다시 시도해주세요."),
    INVALID_SIGNUP_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_SIGNUP_TOKEN", "가입 세션이 만료되었습니다. 다시 시도해주세요."),
    INVALID_LINK_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_LINK_TOKEN", "연동 세션이 만료되었습니다. 다시 시도해주세요."),
    PARENT_NOT_FOUND(HttpStatus.UNAUTHORIZED, "AUTH_PARENT_NOT_FOUND", "계정 정보를 확인할 수 없습니다. 다시 로그인해주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
