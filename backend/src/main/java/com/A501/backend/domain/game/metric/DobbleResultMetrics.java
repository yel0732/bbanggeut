package com.A501.backend.domain.game.metric;

import java.util.List;

public record DobbleResultMetrics(
        int solvedCardCount,
        int perfectCount,
        int excellentCount,
        int goodCount,
        int incompleteCardCount,
        long averageResponseTimeMs,
        List<CardResultMetrics> cardResults
) implements GameResultMetrics {

    private static final int PERFECT_SCORE = 1000;
    private static final int EXCELLENT_SCORE = 850;
    private static final int GOOD_SCORE = 700;

    public DobbleResultMetrics {
        if (solvedCardCount < 0 || perfectCount < 0 || excellentCount < 0
                || goodCount < 0 || incompleteCardCount < 0) {
            throw new IllegalArgumentException("카드 결과 개수는 0 이상이어야 합니다.");
        }
        if (averageResponseTimeMs < 0) {
            throw new IllegalArgumentException("평균 반응시간은 0 이상이어야 합니다.");
        }
        if (cardResults == null) {
            throw new IllegalArgumentException("카드별 결과는 필수입니다.");
        }

        cardResults = List.copyOf(cardResults);

        if (solvedCardCount != perfectCount + excellentCount + goodCount) {
            throw new IllegalArgumentException("해결 카드 수는 등급별 카드 수의 합과 같아야 합니다.");
        }
        if (solvedCardCount != countByStatus(cardResults, CardResultStatus.COMPLETED)
                || incompleteCardCount != countByStatus(cardResults, CardResultStatus.INCOMPLETE)) {
            throw new IllegalArgumentException("카드 상태별 개수와 카드별 결과가 일치해야 합니다.");
        }
        if (perfectCount != countByGrade(cardResults, CognitiveGrade.PERFECT)
                || excellentCount != countByGrade(cardResults, CognitiveGrade.EXCELLENT)
                || goodCount != countByGrade(cardResults, CognitiveGrade.GOOD)) {
            throw new IllegalArgumentException("등급별 카드 수와 카드별 결과가 일치해야 합니다.");
        }
    }

    @Override
    public GameMetricType metricType() {
        return GameMetricType.DOBBLE;
    }

    public int calculatedScore() {
        return perfectCount * PERFECT_SCORE
                + excellentCount * EXCELLENT_SCORE
                + goodCount * GOOD_SCORE;
    }

    private static long countByStatus(List<CardResultMetrics> cardResults, CardResultStatus status) {
        return cardResults.stream()
                .filter(result -> result.status() == status)
                .count();
    }

    private static long countByGrade(List<CardResultMetrics> cardResults, CognitiveGrade grade) {
        return cardResults.stream()
                .filter(result -> result.finalGrade() == grade)
                .count();
    }
}
