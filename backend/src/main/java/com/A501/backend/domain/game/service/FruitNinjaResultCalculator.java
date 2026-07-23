package com.A501.backend.domain.game.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.game.entity.FruitScoreConfig;
import com.A501.backend.domain.game.entity.MovementGameRuleVersion;
import com.A501.backend.domain.game.exception.GameErrorCode;
import com.A501.backend.domain.game.exception.GameException;
import com.A501.backend.domain.game.metric.FruitNinjaCalculationResult;
import com.A501.backend.domain.game.metric.FruitResultCount;
import com.A501.backend.domain.game.repository.FruitScoreConfigRepository;
import com.A501.backend.domain.game.repository.MovementGameRuleVersionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FruitNinjaResultCalculator {

    private final MovementGameRuleVersionRepository ruleVersionRepository;
    private final FruitScoreConfigRepository fruitScoreConfigRepository;

    @Transactional(readOnly = true)
    public FruitNinjaCalculationResult calculate(
            String rulesVersion,
            int totalFruitCount,
            int slicedFruitCount,
            List<FruitResultCount> fruitResults) {
        validateRulesVersion(rulesVersion);
        validateTotals(totalFruitCount, slicedFruitCount, fruitResults);

        MovementGameRuleVersion ruleVersion = ruleVersionRepository.findByVersionCode(rulesVersion)
                .orElseThrow(() -> new GameException(GameErrorCode.FRUIT_NINJA_RULE_NOT_FOUND));
        List<FruitScoreConfig> scoreConfigs =
                fruitScoreConfigRepository.findAllByRuleVersion(ruleVersion);
        Map<String, Integer> scoresByFruitCode = toScoreMap(scoreConfigs);

        int calculatedScore = calculateScore(fruitResults, scoresByFruitCode);
        return new FruitNinjaCalculationResult(
                ruleVersion.getVersionCode(),
                ruleVersion.getPlannedDurationSeconds(),
                totalFruitCount,
                slicedFruitCount,
                calculatedScore,
                fruitResults);
    }

    private void validateRulesVersion(String rulesVersion) {
        if (rulesVersion == null || rulesVersion.isBlank()) {
            throw new GameException(GameErrorCode.INVALID_FRUIT_NINJA_RESULT);
        }
    }

    private void validateTotals(
            int totalFruitCount,
            int slicedFruitCount,
            List<FruitResultCount> fruitResults) {
        if (totalFruitCount < 0 || slicedFruitCount < 0 || slicedFruitCount > totalFruitCount) {
            throw new GameException(GameErrorCode.INVALID_FRUIT_NINJA_RESULT);
        }
        if (fruitResults == null) {
            throw new GameException(GameErrorCode.INVALID_FRUIT_NINJA_RESULT);
        }

        Set<String> uniqueFruitCodes = new HashSet<>();
        long generatedSum = 0;
        long slicedSum = 0;
        for (FruitResultCount fruitResult : fruitResults) {
            if (fruitResult == null || !uniqueFruitCodes.add(fruitResult.fruitCode())) {
                throw new GameException(GameErrorCode.INVALID_FRUIT_NINJA_RESULT);
            }
            generatedSum += fruitResult.generatedCount();
            slicedSum += fruitResult.slicedCount();
        }
        if (generatedSum != totalFruitCount || slicedSum != slicedFruitCount) {
            throw new GameException(GameErrorCode.INVALID_FRUIT_NINJA_RESULT);
        }
    }

    private Map<String, Integer> toScoreMap(List<FruitScoreConfig> scoreConfigs) {
        Map<String, Integer> scoresByFruitCode = new HashMap<>();
        for (FruitScoreConfig config : scoreConfigs) {
            if (config.getScore() > FruitScoreConfig.MAX_FRUIT_SCORE) {
                throw new GameException(GameErrorCode.INVALID_FRUIT_NINJA_RULE);
            }
            scoresByFruitCode.put(config.getFruitType().getFruitCode(), config.getScore());
        }
        return scoresByFruitCode;
    }

    private int calculateScore(
            List<FruitResultCount> fruitResults,
            Map<String, Integer> scoresByFruitCode) {
        long score = 0;
        for (FruitResultCount fruitResult : fruitResults) {
            Integer fruitScore = scoresByFruitCode.get(fruitResult.fruitCode());
            if (fruitScore == null) {
                throw new GameException(GameErrorCode.FRUIT_NOT_FOUND_IN_RULE);
            }
            score += (long) fruitResult.slicedCount() * fruitScore;
        }
        if (score > Integer.MAX_VALUE) {
            throw new GameException(GameErrorCode.INVALID_FRUIT_NINJA_RESULT);
        }
        return (int) score;
    }
}
