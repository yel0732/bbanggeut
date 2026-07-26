package com.A501.backend.domain.conversation.dto.internal;

import java.time.LocalDateTime;
import java.util.UUID;

import com.A501.backend.domain.game.model.CompletedGameSessionSummary;

public record DialogueGameContextPayload(
        UUID gameSessionId,
        Long gameId,
        int score,
        int playDurationSeconds,
        LocalDateTime completedAt) {

    public static DialogueGameContextPayload from(
            CompletedGameSessionSummary summary) {
        return new DialogueGameContextPayload(
                summary.gameSessionId(),
                summary.gameId(),
                summary.score(),
                summary.playDurationSeconds(),
                summary.completedAt());
    }
}
