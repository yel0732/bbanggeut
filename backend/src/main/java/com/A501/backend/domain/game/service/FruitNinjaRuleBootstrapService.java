package com.A501.backend.domain.game.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.game.entity.FruitScoreConfig;
import com.A501.backend.domain.game.entity.FruitScoreConfigId;
import com.A501.backend.domain.game.entity.FruitType;
import com.A501.backend.domain.game.entity.MovementGameRuleVersion;
import com.A501.backend.domain.game.repository.FruitScoreConfigRepository;
import com.A501.backend.domain.game.repository.FruitTypeRepository;
import com.A501.backend.domain.game.repository.MovementGameRuleVersionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FruitNinjaRuleBootstrapService {

    static final String RULES_VERSION = "fruit-ninja-v1";
    static final int PLANNED_DURATION_SECONDS = 60;
    static final BigDecimal SLICE_SPEED_THRESHOLD = new BigDecimal("950.0000");
    static final int FRUIT_SPAWN_INTERVAL_MS = 1100;
    static final int MAX_VISIBLE_FRUIT = 5;

    private static final List<FruitSeed> FRUITS = List.of(
            new FruitSeed("grape", "포도", 96, 50),
            new FruitSeed("strawberry", "딸기", 104, 45),
            new FruitSeed("kiwi", "키위", 112, 40),
            new FruitSeed("apple", "사과", 122, 35),
            new FruitSeed("orange", "오렌지", 132, 30),
            new FruitSeed("banana", "바나나", 156, 28),
            new FruitSeed("mango", "망고", 156, 25),
            new FruitSeed("coconut", "코코넛", 168, 20),
            new FruitSeed("pineapple", "파인애플", 180, 18),
            new FruitSeed("watermelon", "수박", 202, 12));

    private final MovementGameRuleVersionRepository ruleVersionRepository;
    private final FruitTypeRepository fruitTypeRepository;
    private final FruitScoreConfigRepository fruitScoreConfigRepository;

    @Transactional
    public void initialize() {
        MovementGameRuleVersion ruleVersion = findOrCreateRuleVersion();
        validateRuleVersion(ruleVersion);
        validateSingleActiveRule(ruleVersion);

        for (FruitSeed seed : FRUITS) {
            FruitType fruitType = findOrCreateFruitType(seed);
            validateFruitType(fruitType, seed);
            findOrCreateScoreConfig(ruleVersion, fruitType, seed);
        }
    }

    private MovementGameRuleVersion findOrCreateRuleVersion() {
        return ruleVersionRepository.findByVersionCode(RULES_VERSION)
                .orElseGet(() -> {
                    if (!ruleVersionRepository.findAllByActiveTrueOrderByCreatedAtDesc().isEmpty()) {
                        throw new IllegalStateException(
                                "다른 활성 후르츠 게임 규칙이 있어 fruit-ninja-v1을 초기화할 수 없습니다.");
                    }
                    return ruleVersionRepository.saveAndFlush(
                            MovementGameRuleVersion.create(
                                    RULES_VERSION,
                                    PLANNED_DURATION_SECONDS,
                                    SLICE_SPEED_THRESHOLD,
                                    FRUIT_SPAWN_INTERVAL_MS,
                                    MAX_VISIBLE_FRUIT,
                                    true));
                });
    }

    private FruitType findOrCreateFruitType(FruitSeed seed) {
        return fruitTypeRepository.findByFruitCode(seed.code())
                .orElseGet(() -> fruitTypeRepository.saveAndFlush(
                        FruitType.create(seed.code(), seed.name(), seed.sizePx())));
    }

    private void findOrCreateScoreConfig(
            MovementGameRuleVersion ruleVersion,
            FruitType fruitType,
            FruitSeed seed) {
        FruitScoreConfigId id = new FruitScoreConfigId(
                ruleVersion.getRuleVersionId(),
                fruitType.getFruitTypeId());
        fruitScoreConfigRepository.findById(id)
                .ifPresentOrElse(
                        config -> validateScoreConfig(config, seed),
                        () -> fruitScoreConfigRepository.save(
                                FruitScoreConfig.create(ruleVersion, fruitType, seed.score())));
    }

    private void validateRuleVersion(MovementGameRuleVersion ruleVersion) {
        boolean matches = ruleVersion.isActive()
                && ruleVersion.getPlannedDurationSeconds() == PLANNED_DURATION_SECONDS
                && ruleVersion.getSliceSpeedThreshold().compareTo(SLICE_SPEED_THRESHOLD) == 0
                && ruleVersion.getFruitSpawnIntervalMs() == FRUIT_SPAWN_INTERVAL_MS
                && ruleVersion.getMaxVisibleFruit() == MAX_VISIBLE_FRUIT;
        if (!matches) {
            throw new IllegalStateException(
                    "기존 fruit-ninja-v1 규칙이 프론트엔드 계약과 일치하지 않습니다.");
        }
    }

    private void validateSingleActiveRule(MovementGameRuleVersion expectedRule) {
        List<MovementGameRuleVersion> activeRules =
                ruleVersionRepository.findAllByActiveTrueOrderByCreatedAtDesc();
        if (activeRules.size() != 1
                || !activeRules.getFirst().getRuleVersionId()
                        .equals(expectedRule.getRuleVersionId())) {
            throw new IllegalStateException(
                    "활성 후르츠 게임 규칙은 fruit-ninja-v1 하나만 존재해야 합니다.");
        }
    }

    private void validateFruitType(FruitType fruitType, FruitSeed seed) {
        if (!fruitType.getName().equals(seed.name())
                || fruitType.getDefaultSizePx() != seed.sizePx()) {
            throw new IllegalStateException(
                    "기존 과일 설정이 프론트엔드 계약과 일치하지 않습니다: " + seed.code());
        }
    }

    private void validateScoreConfig(FruitScoreConfig config, FruitSeed seed) {
        if (config.getScore() != seed.score()) {
            throw new IllegalStateException(
                    "기존 과일 점수가 프론트엔드 계약과 일치하지 않습니다: " + seed.code());
        }
    }

    private record FruitSeed(String code, String name, int sizePx, int score) {
    }
}
