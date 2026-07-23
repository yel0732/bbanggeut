package com.A501.backend.domain.game.dto.response;

import com.A501.backend.domain.game.entity.FruitScoreConfig;

public record FruitRuleResponse(
        String fruitCode,
        String name,
        int sizePx,
        int score
) {

    public static FruitRuleResponse from(FruitScoreConfig config) {
        return new FruitRuleResponse(
                config.getFruitType().getFruitCode(),
                config.getFruitType().getName(),
                config.getFruitType().getDefaultSizePx(),
                config.getScore());
    }
}
