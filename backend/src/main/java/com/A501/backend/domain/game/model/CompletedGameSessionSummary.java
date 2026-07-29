package com.A501.backend.domain.game.model;

import java.time.LocalDateTime;
import java.util.UUID;

import com.A501.backend.domain.game.entity.GameSession;

public record CompletedGameSessionSummary(
        UUID gameSessionId,
        Long gameId,
        String gameName,
        int score,
        int playDurationSeconds,
        LocalDateTime completedAt) {

    /** 카탈로그에 없는 게임은 이름 없이 요약한다 — 대화는 이름 없이도 진행돼야 한다. */
    public static CompletedGameSessionSummary from(GameSession session) {
        return from(session, null);
    }

    public static CompletedGameSessionSummary from(GameSession session, String gameName) {
        return new CompletedGameSessionSummary(
                session.getGameSessionId(),
                session.getGameId(),
                gameName,
                session.getScore(),
                session.getPlayDurationSeconds(),
                session.getCompletedAt());
    }
}
