package com.A501.backend.domain.game.metric;

public sealed interface GameResultMetrics permits DobbleResultMetrics, FruitNinjaResultMetrics {

    GameMetricType metricType();
}
