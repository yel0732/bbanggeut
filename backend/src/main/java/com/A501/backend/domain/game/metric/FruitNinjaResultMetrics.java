package com.A501.backend.domain.game.metric;

public record FruitNinjaResultMetrics(
        int totalFruitCount,
        int slicedFruitCount,
        String rulesVersion
) implements GameResultMetrics {

    public FruitNinjaResultMetrics {
        if (totalFruitCount < 0 || slicedFruitCount < 0) {
            throw new IllegalArgumentException("과일 결과 개수는 0 이상이어야 합니다.");
        }
        if (slicedFruitCount > totalFruitCount) {
            throw new IllegalArgumentException("자른 과일 수는 전체 등장 과일 수를 초과할 수 없습니다.");
        }
        if (rulesVersion == null || rulesVersion.isBlank()) {
            throw new IllegalArgumentException("게임 규칙 버전은 필수입니다.");
        }
    }

    @Override
    public GameMetricType metricType() {
        return GameMetricType.FRUIT_NINJA;
    }
}
