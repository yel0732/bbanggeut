package com.A501.backend.domain.game.dto.response;

import com.A501.backend.domain.game.entity.RecyclingGameResult;

public record RecyclingGameResultResponse(
        String rulesVersion,
        int successfulCount,
        int wrongCount
) {

    public static RecyclingGameResultResponse from(RecyclingGameResult result) {
        return new RecyclingGameResultResponse(
                result.getRulesVersion(),
                result.getSuccessfulCount(),
                result.getWrongCount());
    }
}
