package com.A501.backend.domain.game.dto.response;

import com.A501.backend.domain.game.entity.WordMatchResult;

public record WordMatchGameResultResponse(
        String rulesVersion,
        int solvedWordCount,
        int incompleteWordCount,
        int wrongCount,
        int hintUsedCount
) {
    public static WordMatchGameResultResponse from(WordMatchResult result) {
        return new WordMatchGameResultResponse(
                result.getRulesVersion(),
                result.getSolvedWordCount(),
                result.getIncompleteWordCount(),
                result.getWrongCount(),
                result.getHintUsedCount());
    }
}
