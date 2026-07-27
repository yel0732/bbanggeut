package com.A501.backend.domain.report.model;

public record DailyReportGenerationResult(
        Long reportId,
        int gameResultCount,
        boolean conversationSummaryIncluded
) {
    public boolean hasActivity() {
        return gameResultCount > 0 || conversationSummaryIncluded;
    }
}
