package com.A501.backend.domain.game.entity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class MovementGameRuleValidationTest {

    @Test
    void 과일_하나의_점수는_500점을_초과할_수_없다() {
        assertThatThrownBy(() -> FruitScoreConfig.create(null, null, 501))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 베기_기준_속도는_0보다_커야_한다() {
        assertThatThrownBy(() -> MovementGameRuleVersion.create(
                "fruit-v1",
                120,
                BigDecimal.ZERO,
                800,
                5,
                true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
