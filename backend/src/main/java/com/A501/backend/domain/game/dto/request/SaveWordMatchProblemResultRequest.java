package com.A501.backend.domain.game.dto.request;

import com.A501.backend.domain.game.entity.WordMatchProblemStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record SaveWordMatchProblemResultRequest(
        @NotBlank @Size(max = 40) String problemCode,
        @NotNull @Positive Integer questionOrder,
        @NotNull @Min(2) @Max(4) Integer wordLength,
        @NotNull WordMatchProblemStatus status,
        @NotNull @PositiveOrZero Integer wrongCount,
        boolean hintUsed,
        @NotNull @PositiveOrZero Integer earnedScore,
        @Positive Long solveTimeMs
) {
}
