package com.A501.backend.domain.game.dto.request;

import com.A501.backend.domain.game.metric.CardResultMetrics;
import com.A501.backend.domain.game.metric.CardResultStatus;
import com.A501.backend.domain.game.metric.CognitiveGrade;
import com.A501.backend.domain.game.metric.DobbleResultCalculator;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record SaveDobbleCardResultRequest(
        @NotNull @Positive Integer roundNo,
        @NotNull @PositiveOrZero Integer comparisonCount,
        @NotNull @PositiveOrZero Integer wrongCount,
        @NotNull CardResultStatus status,
        @Min(0) Long solveTimeMs
) {

    public CardResultMetrics toMetrics() {
        CognitiveGrade grade = status == CardResultStatus.COMPLETED
                ? DobbleResultCalculator.gradeFor(comparisonCount)
                : null;

        return new CardResultMetrics(
                "round-" + roundNo,
                comparisonCount,
                wrongCount,
                status,
                grade,
                false,
                solveTimeMs);
    }
}
