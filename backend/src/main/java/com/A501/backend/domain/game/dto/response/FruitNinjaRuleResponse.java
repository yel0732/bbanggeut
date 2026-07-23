package com.A501.backend.domain.game.dto.response;

import java.math.BigDecimal;
import java.util.List;

import com.A501.backend.domain.game.entity.FruitScoreConfig;
import com.A501.backend.domain.game.entity.MovementGameRuleVersion;

public record FruitNinjaRuleResponse(
        String rulesVersion,
        int plannedDurationSeconds,
        BigDecimal sliceSpeedThreshold,
        int fruitSpawnIntervalMs,
        int maxVisibleFruit,
        List<FruitRuleResponse> fruits
) {

    public FruitNinjaRuleResponse {
        fruits = List.copyOf(fruits);
    }

    public static FruitNinjaRuleResponse from(
            MovementGameRuleVersion ruleVersion,
            List<FruitScoreConfig> fruitConfigs) {
        return new FruitNinjaRuleResponse(
                ruleVersion.getVersionCode(),
                ruleVersion.getPlannedDurationSeconds(),
                ruleVersion.getSliceSpeedThreshold(),
                ruleVersion.getFruitSpawnIntervalMs(),
                ruleVersion.getMaxVisibleFruit(),
                fruitConfigs.stream()
                        .map(FruitRuleResponse::from)
                        .toList());
    }
}
