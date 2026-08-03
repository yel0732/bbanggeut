package com.A501.backend.domain.report.dto.response;

import java.util.Map;

public record ReportGameSummaryResponse(
        String gameType,
        String gameName,
        int playCount,
        long totalPlayDurationSeconds,
        int highestScore,
        Map<String, Long> resultMetrics
) {
    public ReportGameSummaryResponse {
        resultMetrics = Map.copyOf(resultMetrics);
    }
}
