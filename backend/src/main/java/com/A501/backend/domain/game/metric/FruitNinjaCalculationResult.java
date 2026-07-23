package com.A501.backend.domain.game.metric;

import java.util.List;

public record FruitNinjaCalculationResult(
        String rulesVersion,
        int totalFruitCount,
        int slicedFruitCount,
        int calculatedScore,
        List<FruitResultCount> fruitResults
) {
    public FruitNinjaCalculationResult {
        fruitResults = List.copyOf(fruitResults);
    }
}
