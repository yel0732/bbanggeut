package com.A501.backend.domain.game.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.A501.backend.domain.game.entity.GameSession;

public record GameSessionResponse(
        UUID gameSessionId,
        UUID clientPlayId,
        LocalDateTime createdAt
) {

    public static GameSessionResponse from(GameSession gameSession) {
        return new GameSessionResponse(
                gameSession.getGameSessionId(),
                gameSession.getClientPlayId(),
                gameSession.getCreatedAt());
    }
}
