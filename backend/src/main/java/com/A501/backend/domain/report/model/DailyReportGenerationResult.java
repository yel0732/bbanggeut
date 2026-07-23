package com.A501.backend.domain.report.model;

public record DailyReportGenerationResult(
        Long reportId,
        int gameResultCount,
        boolean conversationSummaryIncluded
) {
}
