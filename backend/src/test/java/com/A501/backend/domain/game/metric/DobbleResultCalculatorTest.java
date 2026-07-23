package com.A501.backend.domain.game.metric;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class DobbleResultCalculatorTest {

    @Test
    void 판정_횟수에_따라_등급을_결정한다() {
        assertThat(DobbleResultCalculator.gradeFor(1)).isEqualTo(CognitiveGrade.PERFECT);
        assertThat(DobbleResultCalculator.gradeFor(2)).isEqualTo(CognitiveGrade.EXCELLENT);
        assertThat(DobbleResultCalculator.gradeFor(4)).isEqualTo(CognitiveGrade.EXCELLENT);
        assertThat(DobbleResultCalculator.gradeFor(5)).isEqualTo(CognitiveGrade.GOOD);
        assertThat(DobbleResultCalculator.gradeFor(10)).isEqualTo(CognitiveGrade.GOOD);
    }

    @Test
    void 완료_카드의_판정_횟수가_0이면_거부한다() {
        CardResultMetrics invalid = completedCard(
                "card-1",
                0,
                0,
                CognitiveGrade.PERFECT,
                1000L);

        assertThatThrownBy(() -> DobbleResultCalculator.calculate(List.of(invalid)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 이상");
    }

    @Test
    void 판정_횟수와_등급이_일치하지_않으면_거부한다() {
        CardResultMetrics manipulated = completedCard(
                "card-1",
                5,
                4,
                CognitiveGrade.PERFECT,
                1000L);

        assertThatThrownBy(() -> DobbleResultCalculator.calculate(List.of(manipulated)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("card-1");
    }

    @Test
    void 카드별_결과에서_합계와_평균_반응시간과_점수를_계산한다() {
        List<CardResultMetrics> cardResults = List.of(
                completedCard("card-1", 1, 0, CognitiveGrade.PERFECT, 1000L),
                completedCard("card-2", 2, 1, CognitiveGrade.EXCELLENT, 2000L),
                completedCard("card-3", 5, 4, CognitiveGrade.GOOD, 3000L),
                incompleteCard("card-4", 1, 1));

        DobbleResultMetrics result = DobbleResultCalculator.calculate(cardResults);

        assertThat(result.solvedCardCount()).isEqualTo(3);
        assertThat(result.incompleteCardCount()).isEqualTo(1);
        assertThat(result.perfectCount()).isEqualTo(1);
        assertThat(result.excellentCount()).isEqualTo(1);
        assertThat(result.goodCount()).isEqualTo(1);
        assertThat(result.averageResponseTimeMs()).isEqualTo(2000L);
        assertThat(result.calculatedScore()).isEqualTo(2550);
    }

    @Test
    void 평균_반응시간은_완료된_카드만_대상으로_반올림한다() {
        List<CardResultMetrics> cardResults = List.of(
                completedCard("card-1", 1, 0, CognitiveGrade.PERFECT, 1000L),
                completedCard("card-2", 2, 1, CognitiveGrade.EXCELLENT, 1001L),
                incompleteCard("card-3", 1, 1));

        DobbleResultMetrics result = DobbleResultCalculator.calculate(cardResults);

        assertThat(result.averageResponseTimeMs()).isEqualTo(1001L);
    }

    @Test
    void 해결한_카드가_없으면_평균_반응시간과_점수는_0이다() {
        DobbleResultMetrics result = DobbleResultCalculator.calculate(List.of(
                incompleteCard("card-1", 0, 0)));

        assertThat(result.solvedCardCount()).isZero();
        assertThat(result.incompleteCardCount()).isEqualTo(1);
        assertThat(result.averageResponseTimeMs()).isZero();
        assertThat(result.calculatedScore()).isZero();
    }

    @Test
    void 입력_목록에_null이_포함되면_거부한다() {
        assertThatThrownBy(() -> DobbleResultCalculator.calculate(
                java.util.Arrays.asList(
                        completedCard("card-1", 1, 0, CognitiveGrade.PERFECT, 1000L),
                        null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
    }

    private CardResultMetrics completedCard(
            String cardKey,
            int comparisonCount,
            int wrongCount,
            CognitiveGrade grade,
            long solveTimeMs) {
        return new CardResultMetrics(
                cardKey,
                comparisonCount,
                wrongCount,
                CardResultStatus.COMPLETED,
                grade,
                false,
                solveTimeMs);
    }

    private CardResultMetrics incompleteCard(
            String cardKey,
            int comparisonCount,
            int wrongCount) {
        return new CardResultMetrics(
                cardKey,
                comparisonCount,
                wrongCount,
                CardResultStatus.INCOMPLETE,
                null,
                false,
                null);
    }
}
