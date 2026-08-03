package com.A501.backend.domain.game.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.A501.backend.domain.game.entity.RecyclingGameResult;
import com.A501.backend.domain.game.entity.GameSession;
import com.A501.backend.domain.game.entity.WordMatchResult;
import com.A501.backend.domain.game.metric.GameMetricType;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GameSessionResponse(
        UUID gameSessionId,
        UUID clientPlayId,
        int score,
        LocalDateTime createdAt,
        GameMetricType gameType,
        RecyclingGameResultResponse recyclingResult,
        WordMatchGameResultResponse wordMatchResult
) {

    public static GameSessionResponse from(GameSession gameSession) {
        return new GameSessionResponse(
                gameSession.getGameSessionId(),
                gameSession.getClientPlayId(),
                gameSession.getScore(),
                gameSession.getCreatedAt(),
                null,
                null,
                null);
    }

    public static GameSessionResponse from(
            GameSession gameSession,
            RecyclingGameResult recyclingGameResult) {
        return new GameSessionResponse(
                gameSession.getGameSessionId(),
                gameSession.getClientPlayId(),
                gameSession.getScore(),
                gameSession.getCreatedAt(),
                GameMetricType.RECYCLING,
                RecyclingGameResultResponse.from(recyclingGameResult),
                null);
    }

    public static GameSessionResponse from(
            GameSession gameSession,
            WordMatchResult wordMatchResult) {
        return new GameSessionResponse(
                gameSession.getGameSessionId(),
                gameSession.getClientPlayId(),
                gameSession.getScore(),
                gameSession.getCreatedAt(),
                GameMetricType.WORD_MATCH,
                null,
                WordMatchGameResultResponse.from(wordMatchResult));
    }
}
