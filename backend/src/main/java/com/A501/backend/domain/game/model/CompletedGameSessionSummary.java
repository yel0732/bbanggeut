package com.A501.backend.domain.game.model;

import java.time.LocalDateTime;
import java.util.UUID;

import com.A501.backend.domain.game.entity.GameSession;

public record CompletedGameSessionSummary(
        UUID gameSessionId,
        Long gameId,
        int score,
        int playDurationSeconds,
        LocalDateTime completedAt) {

    public static CompletedGameSessionSummary from(GameSession session) {
        return new CompletedGameSessionSummary(
                session.getGameSessionId(),
                session.getGameId(),
                session.getScore(),
                session.getPlayDurationSeconds(),
                session.getCompletedAt());
    }
}
