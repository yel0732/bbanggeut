package com.A501.backend.domain.game.metric;

public record FruitResultCount(
        String fruitCode,
        int generatedCount,
        int slicedCount
) {
    public FruitResultCount {
        if (fruitCode == null || fruitCode.isBlank()) {
            throw new IllegalArgumentException("과일 코드는 필수입니다.");
        }
        if (generatedCount < 0 || slicedCount < 0) {
            throw new IllegalArgumentException("과일 개수는 0 이상이어야 합니다.");
        }
        if (slicedCount > generatedCount) {
            throw new IllegalArgumentException("자른 과일 수는 생성된 과일 수를 초과할 수 없습니다.");
        }
    }
}
