package com.A501.backend.domain.game.metric;

public record CardResultMetrics(
        String cardKey,
        int comparisonCount,
        int wrongCount,
        CardResultStatus status,
        CognitiveGrade finalGrade,
        boolean hintUsed,
        Long solveTimeMs
) {

    public CardResultMetrics {
        if (cardKey == null || cardKey.isBlank()) {
            throw new IllegalArgumentException("cardKey는 비어 있을 수 없습니다.");
        }
        if (comparisonCount < 0 || wrongCount < 0) {
            throw new IllegalArgumentException("비교 및 오답 횟수는 0 이상이어야 합니다.");
        }
        if (wrongCount > comparisonCount) {
            throw new IllegalArgumentException("오답 횟수는 비교 판정 횟수를 초과할 수 없습니다.");
        }
        if (status == null) {
            throw new IllegalArgumentException("카드 결과 상태는 필수입니다.");
        }
        if (status == CardResultStatus.COMPLETED && (finalGrade == null || solveTimeMs == null)) {
            throw new IllegalArgumentException("완료 카드에는 최종 등급과 해결 시간이 필요합니다.");
        }
        if (status == CardResultStatus.INCOMPLETE && (finalGrade != null || solveTimeMs != null)) {
            throw new IllegalArgumentException("미완료 카드에는 최종 등급과 해결 시간을 기록하지 않습니다.");
        }
        if (solveTimeMs != null && solveTimeMs < 0) {
            throw new IllegalArgumentException("카드 해결 시간은 0 이상이어야 합니다.");
        }
    }
}
