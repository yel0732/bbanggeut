package com.A501.backend.domain.game.dto.request;

import java.time.LocalDateTime;
import java.util.UUID;

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
        @NotNull @PositiveOrZero Integer score
) {
}
