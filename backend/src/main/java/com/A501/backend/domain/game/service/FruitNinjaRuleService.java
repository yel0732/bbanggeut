package com.A501.backend.domain.game.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.game.dto.response.FruitNinjaRuleResponse;
import com.A501.backend.domain.game.entity.FruitScoreConfig;
import com.A501.backend.domain.game.entity.MovementGameRuleVersion;
import com.A501.backend.domain.game.exception.GameErrorCode;
import com.A501.backend.domain.game.exception.GameException;
import com.A501.backend.domain.game.repository.FruitScoreConfigRepository;
import com.A501.backend.domain.game.repository.MovementGameRuleVersionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FruitNinjaRuleService {

    private final MovementGameRuleVersionRepository ruleVersionRepository;
    private final FruitScoreConfigRepository fruitScoreConfigRepository;

    public FruitNinjaRuleResponse getActiveRule() {
        List<MovementGameRuleVersion> activeRules =
                ruleVersionRepository.findAllByActiveTrueOrderByCreatedAtDesc();

        if (activeRules.isEmpty()) {
            throw new GameException(GameErrorCode.ACTIVE_FRUIT_NINJA_RULE_NOT_FOUND);
        }
        if (activeRules.size() > 1) {
            throw new GameException(GameErrorCode.MULTIPLE_ACTIVE_FRUIT_NINJA_RULES);
        }

        MovementGameRuleVersion activeRule = activeRules.getFirst();
        List<FruitScoreConfig> fruitConfigs =
                fruitScoreConfigRepository.findAllByRuleVersion(activeRule);
        if (fruitConfigs.isEmpty()) {
            throw new GameException(GameErrorCode.EMPTY_FRUIT_NINJA_RULE);
        }

        return FruitNinjaRuleResponse.from(activeRule, fruitConfigs);
    }
}
