package com.A501.backend.domain.game.metric;

import java.util.List;
import java.util.Objects;

public final class DobbleResultCalculator {

    private DobbleResultCalculator() {
    }

    public static DobbleResultMetrics calculate(List<CardResultMetrics> cardResults) {
        if (cardResults == null) {
            throw new IllegalArgumentException("카드별 결과는 필수입니다.");
        }

        if (cardResults.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("카드 결과에는 null이 포함될 수 없습니다.");
        }

        List<CardResultMetrics> copiedResults = List.copyOf(cardResults);
        copiedResults.forEach(DobbleResultCalculator::validateGrade);

        int solvedCardCount = countByStatus(copiedResults, CardResultStatus.COMPLETED);
        int incompleteCardCount = countByStatus(copiedResults, CardResultStatus.INCOMPLETE);
        int perfectCount = countByGrade(copiedResults, CognitiveGrade.PERFECT);
        int excellentCount = countByGrade(copiedResults, CognitiveGrade.EXCELLENT);
        int goodCount = countByGrade(copiedResults, CognitiveGrade.GOOD);

        return new DobbleResultMetrics(
                solvedCardCount,
                perfectCount,
                excellentCount,
                goodCount,
                incompleteCardCount,
                copiedResults);
    }

    public static CognitiveGrade gradeFor(int comparisonCount) {
        if (comparisonCount < 1) {
            throw new IllegalArgumentException("완료 카드의 판정 횟수는 1 이상이어야 합니다.");
        }
        if (comparisonCount == 1) {
            return CognitiveGrade.PERFECT;
        }
        if (comparisonCount <= 4) {
            return CognitiveGrade.EXCELLENT;
        }
        return CognitiveGrade.GOOD;
    }

    private static void validateGrade(CardResultMetrics cardResult) {
        if (cardResult == null) {
            throw new IllegalArgumentException("카드 결과에는 null이 포함될 수 없습니다.");
        }
        if (cardResult.status() != CardResultStatus.COMPLETED) {
            return;
        }

        CognitiveGrade expectedGrade = gradeFor(cardResult.comparisonCount());
        if (cardResult.finalGrade() != expectedGrade) {
            throw new IllegalArgumentException(
                    "카드 등급이 판정 횟수와 일치하지 않습니다: " + cardResult.cardKey());
        }
    }

    private static int countByStatus(List<CardResultMetrics> cardResults, CardResultStatus status) {
        return (int) cardResults.stream()
                .filter(result -> result.status() == status)
                .count();
    }

    private static int countByGrade(List<CardResultMetrics> cardResults, CognitiveGrade grade) {
        return (int) cardResults.stream()
                .filter(result -> result.finalGrade() == grade)
                .count();
    }
}
