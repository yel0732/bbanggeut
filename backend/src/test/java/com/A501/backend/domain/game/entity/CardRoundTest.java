package com.A501.backend.domain.game.entity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.A501.backend.domain.game.metric.CardResultStatus;
import com.A501.backend.domain.game.metric.CognitiveGrade;

class CardRoundTest {

    @Test
    void 카드_제시_순서는_1부터_시작한다() {
        assertThatThrownBy(() -> CardRound.create(
                UUID.randomUUID(),
                0,
                CardResultStatus.COMPLETED,
                1,
                0,
                CognitiveGrade.PERFECT,
                1000L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 이상");
    }

    @Test
    void 완료_카드에는_등급과_해결_시간이_필요하다() {
        assertThatThrownBy(() -> CardRound.create(
                UUID.randomUUID(),
                1,
                CardResultStatus.COMPLETED,
                1,
                0,
                null,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("완료 카드");
    }

    @Test
    void 미완료_카드에는_등급과_해결_시간을_기록하지_않는다() {
        assertThatThrownBy(() -> CardRound.create(
                UUID.randomUUID(),
                1,
                CardResultStatus.INCOMPLETE,
                1,
                1,
                CognitiveGrade.GOOD,
                1000L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("미완료 카드");
    }

    @Test
    void 오답_횟수는_판정_횟수를_초과할_수_없다() {
        assertThatThrownBy(() -> CardRound.create(
                UUID.randomUUID(),
                1,
                CardResultStatus.COMPLETED,
                1,
                2,
                CognitiveGrade.GOOD,
                1000L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("올바르지 않습니다");
    }
}
