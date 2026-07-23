package com.A501.backend.domain.game.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.A501.backend.domain.game.entity.FruitScoreConfig;
import com.A501.backend.domain.game.entity.FruitType;
import com.A501.backend.domain.game.entity.MovementGameRuleVersion;
import com.A501.backend.domain.game.exception.GameErrorCode;
import com.A501.backend.domain.game.exception.GameException;
import com.A501.backend.domain.game.metric.FruitResultCount;
import com.A501.backend.domain.game.repository.FruitScoreConfigRepository;
import com.A501.backend.domain.game.repository.MovementGameRuleVersionRepository;

@ExtendWith(MockitoExtension.class)
class FruitNinjaResultCalculatorTest {

    @Mock MovementGameRuleVersionRepository ruleVersionRepository;
    @Mock FruitScoreConfigRepository fruitScoreConfigRepository;

    private FruitNinjaResultCalculator calculator;
    private MovementGameRuleVersion ruleVersion;
    private FruitScoreConfig appleScore;
    private FruitScoreConfig bananaScore;

    @BeforeEach
    void setUp() {
        calculator = new FruitNinjaResultCalculator(ruleVersionRepository, fruitScoreConfigRepository);
        ruleVersion = savedRule("fruit-v1");
        appleScore = FruitScoreConfig.create(ruleVersion, savedFruit(1L, "APPLE"), 100);
        bananaScore = FruitScoreConfig.create(ruleVersion, savedFruit(2L, "BANANA"), 150);
    }

    @Test
    void calculatesScoreFromRuleVersionAndSlicedFruitCounts() {
        when(ruleVersionRepository.findByVersionCode("fruit-v1")).thenReturn(Optional.of(ruleVersion));
        when(fruitScoreConfigRepository.findAllByRuleVersion(ruleVersion))
                .thenReturn(List.of(appleScore, bananaScore));

        var result = calculator.calculate("fruit-v1", 10, 7, List.of(
                new FruitResultCount("APPLE", 6, 5),
                new FruitResultCount("BANANA", 4, 2)));

        assertThat(result.calculatedScore()).isEqualTo(800);
        assertThat(result.totalFruitCount()).isEqualTo(10);
        assertThat(result.slicedFruitCount()).isEqualTo(7);
    }

    @Test
    void rejectsGeneratedCountMismatch() {
        assertGameError(() -> calculator.calculate("fruit-v1", 9, 7, List.of(
                new FruitResultCount("APPLE", 6, 5),
                new FruitResultCount("BANANA", 4, 2))),
                GameErrorCode.INVALID_FRUIT_NINJA_RESULT);
    }

    @Test
    void rejectsSlicedCountMismatch() {
        assertGameError(() -> calculator.calculate("fruit-v1", 10, 6, List.of(
                new FruitResultCount("APPLE", 6, 5),
                new FruitResultCount("BANANA", 4, 2))),
                GameErrorCode.INVALID_FRUIT_NINJA_RESULT);
    }

    @Test
    void rejectsDuplicateFruitCodes() {
        assertGameError(() -> calculator.calculate("fruit-v1", 10, 7, List.of(
                new FruitResultCount("APPLE", 6, 5),
                new FruitResultCount("APPLE", 4, 2))),
                GameErrorCode.INVALID_FRUIT_NINJA_RESULT);
    }

    @Test
    void rejectsFruitThatDoesNotExistInRule() {
        when(ruleVersionRepository.findByVersionCode("fruit-v1")).thenReturn(Optional.of(ruleVersion));
        when(fruitScoreConfigRepository.findAllByRuleVersion(ruleVersion)).thenReturn(List.of(appleScore));

        assertGameError(() -> calculator.calculate("fruit-v1", 2, 1,
                        List.of(new FruitResultCount("BANANA", 2, 1))),
                GameErrorCode.FRUIT_NOT_FOUND_IN_RULE);
    }

    @Test
    void rejectsUnknownRuleVersion() {
        when(ruleVersionRepository.findByVersionCode("unknown")).thenReturn(Optional.empty());

        assertGameError(() -> calculator.calculate("unknown", 1, 1,
                        List.of(new FruitResultCount("APPLE", 1, 1))),
                GameErrorCode.FRUIT_NINJA_RULE_NOT_FOUND);
    }

    private void assertGameError(Runnable action, GameErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(GameException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }

    private MovementGameRuleVersion savedRule(String versionCode) {
        MovementGameRuleVersion rule = MovementGameRuleVersion.create(
                versionCode, 120, BigDecimal.valueOf(1.5), 800, 5, true);
        ReflectionTestUtils.setField(rule, "ruleVersionId", 1L);
        return rule;
    }

    private FruitType savedFruit(Long id, String code) {
        FruitType fruit = FruitType.create(code, code, 120);
        ReflectionTestUtils.setField(fruit, "fruitTypeId", id);
        return fruit;
    }
}
