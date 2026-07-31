package com.A501.backend.domain.game.metric;

public record WordMatchResultMetrics(
        int solvedWordCount,
        int incompleteWordCount,
        int wrongCount,
        int hintUsedCount,
        String rulesVersion
) implements GameResultMetrics {

    public WordMatchResultMetrics {
        if (solvedWordCount < 0 || incompleteWordCount < 0
                || wrongCount < 0 || hintUsedCount < 0) {
            throw new IllegalArgumentException("낱말 맞추기 결과 개수는 0 이상이어야 합니다.");
        }
        if (rulesVersion == null || rulesVersion.isBlank()) {
            throw new IllegalArgumentException("낱말 맞추기 규칙 버전은 필수입니다.");
        }
    }

    @Override
    public GameMetricType metricType() {
        return GameMetricType.WORD_MATCH;
    }
}
