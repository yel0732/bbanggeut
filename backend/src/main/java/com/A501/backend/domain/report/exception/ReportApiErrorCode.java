package com.A501.backend.domain.report.exception;

import org.springframework.http.HttpStatus;

import com.A501.backend.common.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReportApiErrorCode implements ErrorCode {

    UNAUTHORIZED(
            HttpStatus.UNAUTHORIZED,
            "REPORT_UNAUTHORIZED",
            "Authentication is required."),
    INVALID_PARENT_ACCESS_TOKEN(
            HttpStatus.UNAUTHORIZED,
            "INVALID_PARENT_ACCESS_TOKEN",
            "A valid parent access token is required."),
    CHILD_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "CHILD_NOT_FOUND",
            "Child profile not found."),
    REPORT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "REPORT_NOT_FOUND",
            "Daily report not found."),
    REPORT_REGENERATION_NOT_ALLOWED(
            HttpStatus.CONFLICT,
            "REPORT_REGENERATION_NOT_ALLOWED",
            "Only a failed report can be regenerated."),
    IDEMPOTENCY_KEY_CONFLICT(
            HttpStatus.CONFLICT,
            "IDEMPOTENCY_KEY_CONFLICT",
            "The idempotency key was already used for another request.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
