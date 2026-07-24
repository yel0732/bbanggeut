package com.A501.backend.domain.report.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.A501.backend.domain.report.entity.ReportGameResult;

public record ReportGameResultResponse(
        UUID gameSessionId,
        String gameType,
        String gameName,
        int playDurationSeconds,
        int score,
        Object resultMetrics,
        LocalDateTime completedAt
) {

    public static ReportGameResultResponse from(
            ReportGameResult result,
            Object resultMetrics
    ) {
        return new ReportGameResultResponse(
                result.getGameSessionId(),
                result.getGameCode(),
                result.getGameName(),
                result.getPlayDurationSeconds(),
                result.getScore(),
                resultMetrics,
                result.getCompletedAt());
    }
}
