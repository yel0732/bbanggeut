package com.A501.backend.domain.game.dto.request;

import java.util.List;

import com.A501.backend.domain.game.metric.FruitResultCount;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record SaveFruitNinjaResultRequest(
        @NotBlank @Size(max = 40) String rulesVersion,
        @PositiveOrZero int totalFruitCount,
        @PositiveOrZero int slicedFruitCount,
        @NotEmpty @Valid List<SaveFruitResultCountRequest> fruitResults
) {
    public List<FruitResultCount> toMetrics() {
        return fruitResults.stream()
                .map(SaveFruitResultCountRequest::toMetrics)
                .toList();
    }
}
