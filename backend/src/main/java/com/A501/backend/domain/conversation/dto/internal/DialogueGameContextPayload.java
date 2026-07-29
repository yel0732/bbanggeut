package com.A501.backend.domain.conversation.dto.internal;

import java.time.LocalDateTime;
import java.util.UUID;

import com.A501.backend.domain.game.model.CompletedGameSessionSummary;

public record DialogueGameContextPayload(
        UUID gameSessionId,
        Long gameId,
        // 카탈로그에 없는 게임은 null로 남는다 — AI는 이름 없이도 질문할 수 있어야 한다.
        String gameName,
        int score,
        int playDurationSeconds,
        LocalDateTime completedAt) {

    public static DialogueGameContextPayload from(
            CompletedGameSessionSummary summary) {
        return new DialogueGameContextPayload(
                summary.gameSessionId(),
                summary.gameId(),
                summary.gameName(),
                summary.score(),
                summary.playDurationSeconds(),
                summary.completedAt());
    }
}
