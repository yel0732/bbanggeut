package com.A501.backend.common.exception;

import java.time.LocalDateTime;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ErrorResponse {

    private final LocalDateTime timestamp;
    private final int status;
    private final String error;
    private final String message;
    private final Map<String, String> fieldErrors;

    // 특정 에러에서만 의미가 있어 null일 때는 응답에서 아예 뺀다. 기존 에러 응답의 형태를
    // 그대로 두기 위함이다 — 항상 내보내면 모든 에러 본문에 null 필드 두 개가 새로 붙는다.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Integer retryAfterSeconds;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Integer remainingAttempts;

    public static ErrorResponse from(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(errorCode.getStatus().value())
                .error(errorCode.getCode())
                .message(errorCode.getMessage())
                .retryAfterSeconds(e.getRetryAfterSeconds())
                .remainingAttempts(e.getRemainingAttempts())
                .build();
    }

    public static ErrorResponse of(int status, String error, String message) {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status)
                .error(error)
                .message(message)
                .build();
    }

    public static ErrorResponse of(int status, String error, String message, Map<String, String> fieldErrors) {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status)
                .error(error)
                .message(message)
                .fieldErrors(fieldErrors)
                .build();
    }
}
