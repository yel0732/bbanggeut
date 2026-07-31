package com.A501.backend.domain.game.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.game.dto.response.RecyclingRuleResponse;
import com.A501.backend.domain.game.entity.RecyclingItem;
import com.A501.backend.domain.game.entity.RecyclingRuleVersion;
import com.A501.backend.domain.game.exception.GameErrorCode;
import com.A501.backend.domain.game.exception.GameException;
import com.A501.backend.domain.game.repository.RecyclingItemRepository;
import com.A501.backend.domain.game.repository.RecyclingRuleVersionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecyclingRuleService {

    private final RecyclingRuleVersionRepository ruleVersionRepository;
    private final RecyclingItemRepository recyclingItemRepository;

    public RecyclingRuleResponse getActiveRule() {
        List<RecyclingRuleVersion> activeRules =
                ruleVersionRepository.findAllByActiveTrueOrderByCreatedAtDesc();

        if (activeRules.isEmpty()) {
            throw new GameException(GameErrorCode.ACTIVE_RECYCLING_RULE_NOT_FOUND);
        }
        if (activeRules.size() > 1) {
            throw new GameException(GameErrorCode.MULTIPLE_ACTIVE_RECYCLING_RULES);
        }

        List<RecyclingItem> activeItems =
                recyclingItemRepository.findAllByActiveTrueOrderByRecyclingItemIdAsc();
        if (activeItems.isEmpty()) {
            throw new GameException(GameErrorCode.EMPTY_RECYCLING_RULE);
        }

        return RecyclingRuleResponse.from(activeRules.getFirst(), activeItems);
    }
}
