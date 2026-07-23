package com.A501.backend.domain.game.metric;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class GameResultMetricsTest {

    @Test
    void 도블류_결과가_공통_인터페이스를_구현하고_점수를_계산한다() {
        GameResultMetrics metrics = new DobbleResultMetrics(
                3,
                1,
                1,
                1,
                1,
                List.of(
                        completedCard("card-1", CognitiveGrade.PERFECT, 800L),
                        completedCard("card-2", CognitiveGrade.EXCELLENT, 1200L),
                        completedCard("card-3", CognitiveGrade.GOOD, 1600L),
                        incompleteCard("card-4")));

        assertThat(metrics.metricType()).isEqualTo(GameMetricType.DOBBLE);
        assertThat(((DobbleResultMetrics) metrics).calculatedScore()).isEqualTo(2550);
    }

    @Test
    void 도블류의_해결_카드_수와_등급별_합이_다르면_생성할_수_없다() {
        assertThatThrownBy(() -> new DobbleResultMetrics(
                2,
                1,
                0,
                0,
                0,
                List.of(completedCard("card-1", CognitiveGrade.PERFECT, 800L))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("해결 카드 수는 등급별 카드 수의 합과 같아야 합니다.");
    }

    @Test
    void 판정_전에_취소된_카드는_결과_목록에서_제외할_수_있다() {
        DobbleResultMetrics metrics = new DobbleResultMetrics(
                1,
                1,
                0,
                0,
                0,
                List.of(completedCard("card-1", CognitiveGrade.PERFECT, 800L)));

        assertThat(metrics.cardResults()).hasSize(1);
    }

    @Test
    void 자른_과일_수가_전체_등장_과일_수보다_많으면_생성할_수_없다() {
        assertThatThrownBy(() -> new FruitNinjaResultMetrics(10, 11, "fruit-v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("자른 과일 수는 전체 등장 과일 수를 초과할 수 없습니다.");
    }

    @Test
    void 후르츠_닌자_결과가_공통_인터페이스를_구현한다() {
        GameResultMetrics metrics = new FruitNinjaResultMetrics(20, 15, "fruit-v1");

        assertThat(metrics.metricType()).isEqualTo(GameMetricType.FRUIT_NINJA);
    }

    private CardResultMetrics completedCard(String cardKey, CognitiveGrade grade, long solveTimeMs) {
        return new CardResultMetrics(
                cardKey,
                2,
                1,
                CardResultStatus.COMPLETED,
                grade,
                false,
                solveTimeMs);
    }

    private CardResultMetrics incompleteCard(String cardKey) {
        return new CardResultMetrics(
                cardKey,
                1,
                1,
                CardResultStatus.INCOMPLETE,
                null,
                true,
                null);
    }
}
