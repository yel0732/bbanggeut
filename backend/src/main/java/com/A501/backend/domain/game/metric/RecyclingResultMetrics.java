package com.A501.backend.domain.game.metric;

public record RecyclingResultMetrics(
        int successfulCount,
        int wrongCount,
        String rulesVersion
) implements GameResultMetrics {

    public RecyclingResultMetrics {
        if (successfulCount < 0 || wrongCount < 0) {
            throw new IllegalArgumentException("분리수거 성공 및 오답 개수는 0 이상이어야 합니다.");
        }
        if (rulesVersion == null || rulesVersion.isBlank()) {
            throw new IllegalArgumentException("분리수거 규칙 버전은 필수입니다.");
        }
    }

    @Override
    public GameMetricType metricType() {
        return GameMetricType.RECYCLING;
    }
}
