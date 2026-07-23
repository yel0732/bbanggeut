package com.A501.backend.domain.game.dto.request;

import com.A501.backend.domain.game.metric.FruitResultCount;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record SaveFruitResultCountRequest(
        @NotBlank @Size(max = 40) String fruitCode,
        @PositiveOrZero int generatedCount,
        @PositiveOrZero int slicedCount
) {
    public FruitResultCount toMetrics() {
        return new FruitResultCount(fruitCode, generatedCount, slicedCount);
    }
}
