package com.A501.backend.domain.game.exception;

import org.springframework.http.HttpStatus;

import com.A501.backend.common.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GameErrorCode implements ErrorCode {

    ACTIVE_FRUIT_NINJA_RULE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "ACTIVE_FRUIT_NINJA_RULE_NOT_FOUND",
            "활성화된 후르츠 게임 규칙을 찾을 수 없습니다."),
    MULTIPLE_ACTIVE_FRUIT_NINJA_RULES(
            HttpStatus.CONFLICT,
            "MULTIPLE_ACTIVE_FRUIT_NINJA_RULES",
            "활성화된 후르츠 게임 규칙이 여러 개입니다."),
    EMPTY_FRUIT_NINJA_RULE(
            HttpStatus.CONFLICT,
            "EMPTY_FRUIT_NINJA_RULE",
            "활성화된 후르츠 게임 규칙에 과일 설정이 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
