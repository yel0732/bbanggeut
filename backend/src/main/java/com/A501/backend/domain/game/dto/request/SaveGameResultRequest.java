package com.A501.backend.domain.game.dto.request;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.A501.backend.domain.game.metric.GameMetricType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record SaveGameResultRequest(
        @NotNull @Positive Long childId,
        @NotNull @Positive Long gameId,
        @NotNull UUID clientPlayId,
        @Size(max = 30) String difficultyCode,
        @NotNull LocalDateTime selectedAt,
        @NotNull LocalDateTime playStartedAt,
        @NotNull LocalDateTime completedAt,
        @NotNull @PositiveOrZero Integer playDurationSeconds,
        @PositiveOrZero Integer score,
        GameMetricType gameType,
        @Valid List<SaveDobbleCardResultRequest> cardResults
) {
    public SaveGameResultRequest(
            Long childId,
            Long gameId,
            UUID clientPlayId,
            String difficultyCode,
            LocalDateTime selectedAt,
            LocalDateTime playStartedAt,
            LocalDateTime completedAt,
            Integer playDurationSeconds,
            Integer score) {
        this(
                childId,
                gameId,
                clientPlayId,
                difficultyCode,
                selectedAt,
                playStartedAt,
                completedAt,
                playDurationSeconds,
                score,
                null,
                null);
    }
}
