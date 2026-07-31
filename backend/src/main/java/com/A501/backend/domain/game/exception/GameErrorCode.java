package com.A501.backend.domain.game.exception;

import org.springframework.http.HttpStatus;

import com.A501.backend.common.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GameErrorCode implements ErrorCode {

    UNAUTHORIZED(
            HttpStatus.UNAUTHORIZED,
            "UNAUTHORIZED",
            "인증이 필요합니다."),
    CHILD_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "CHILD_NOT_FOUND",
            "아이 프로필을 찾을 수 없습니다."),
    INVALID_FRUIT_NINJA_RESULT(
            HttpStatus.BAD_REQUEST,
            "INVALID_FRUIT_NINJA_RESULT",
            "후르츠 게임 결과의 개수 또는 집계가 올바르지 않습니다."),
    GAME_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "GAME_NOT_FOUND",
            "게임을 찾을 수 없습니다."),
    GAME_TYPE_MISMATCH(
            HttpStatus.BAD_REQUEST,
            "GAME_TYPE_MISMATCH",
            "게임 ID와 게임 유형이 일치하지 않습니다."),
    INVALID_RECYCLING_RESULT(
            HttpStatus.BAD_REQUEST,
            "INVALID_RECYCLING_RESULT",
            "분리수거 게임 결과가 올바르지 않습니다."),
    CLIENT_PLAY_ID_CONFLICT(
            HttpStatus.CONFLICT,
            "CLIENT_PLAY_ID_CONFLICT",
            "이미 다른 게임 결과에 사용된 clientPlayId입니다."),
    INVALID_WORD_MATCH_RESULT(
            HttpStatus.BAD_REQUEST,
            "INVALID_WORD_MATCH_RESULT",
            "낱말 맞추기 결과가 올바르지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
