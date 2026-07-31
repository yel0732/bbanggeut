package com.A501.backend.domain.game.dto.response;

import java.util.Arrays;
import java.util.List;

import com.A501.backend.domain.game.entity.RecyclingCategory;
import com.A501.backend.domain.game.entity.RecyclingItem;
import com.A501.backend.domain.game.entity.RecyclingRuleVersion;

public record RecyclingRuleResponse(
        String rulesVersion,
        int plannedDurationSeconds,
        int trashSpawnIntervalMs,
        int trashMovementDurationMs,
        int maxVisibleTrash,
        int scorePerCorrect,
        int wrongGuideDurationMs,
        List<RecyclingCategoryResponse> categories,
        List<RecyclingItemResponse> items
) {

    public RecyclingRuleResponse {
        categories = List.copyOf(categories);
        items = List.copyOf(items);
    }

    public static RecyclingRuleResponse from(
            RecyclingRuleVersion ruleVersion,
            List<RecyclingItem> items) {
        return new RecyclingRuleResponse(
                ruleVersion.getVersionCode(),
                ruleVersion.getPlannedDurationSeconds(),
                ruleVersion.getTrashSpawnIntervalMs(),
                ruleVersion.getTrashMovementDurationMs(),
                ruleVersion.getMaxVisibleTrash(),
                ruleVersion.getScorePerCorrect(),
                ruleVersion.getWrongGuideDurationMs(),
                Arrays.stream(RecyclingCategory.values())
                        .map(RecyclingCategoryResponse::from)
                        .toList(),
                items.stream()
                        .map(RecyclingItemResponse::from)
                        .toList());
    }
}
