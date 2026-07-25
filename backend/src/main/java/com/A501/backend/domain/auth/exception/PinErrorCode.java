package com.A501.backend.domain.auth.exception;

import org.springframework.http.HttpStatus;

import com.A501.backend.common.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PinErrorCode implements ErrorCode {

    PIN_NOT_SET(HttpStatus.FORBIDDEN, "PIN_NOT_SET", "PIN이 설정되지 않았습니다."),
    PIN_ALREADY_SET(HttpStatus.FORBIDDEN, "PIN_ALREADY_SET", "이미 PIN이 설정되어 있습니다."),
    PIN_MISMATCH(HttpStatus.UNAUTHORIZED, "PIN_MISMATCH", "PIN이 일치하지 않습니다."),
    PIN_CONFIRMATION_MISMATCH(HttpStatus.BAD_REQUEST, "PIN_CONFIRMATION_MISMATCH", "새 PIN과 확인 PIN이 일치하지 않습니다."),
    PIN_LOCKED(HttpStatus.LOCKED, "PIN_LOCKED", "PIN 입력이 잠겼습니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
