package com.A501.backend.domain.report.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.A501.backend.domain.report.entity.DailyReport;
import com.A501.backend.domain.report.entity.ReportGenerationAttempt;

public record ReportDetailResponse(
        Long reportId,
        Long childId,
        LocalDate reportDate,
        ReportAggregationPeriodResponse aggregationPeriod,
        com.A501.backend.domain.report.entity.ReportGenerationStatus status,
        ReportDataAvailabilityResponse dataAvailability,
        List<ReportGameResultResponse> gameResults,
        List<ReportGameSummaryResponse> gameSummaries,
        ReportConversationSummaryResponse conversationSummary,
        ReportFailureResponse failure,
        LocalDateTime generatedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ReportDetailResponse from(
            DailyReport report,
            ReportDataAvailabilityResponse dataAvailability,
            List<ReportGameResultResponse> gameResults,
            List<ReportGameSummaryResponse> gameSummaries,
            ReportConversationSummaryResponse conversationSummary,
            ReportGenerationAttempt lastAttempt
    ) {
        return new ReportDetailResponse(
                report.getId(),
                report.getChildId(),
                report.getReportDate(),
                ReportAggregationPeriodResponse.from(report),
                report.getGenerationStatus(),
                dataAvailability,
                gameResults,
                gameSummaries,
                conversationSummary,
                ReportFailureResponse.from(report, lastAttempt),
                report.getCompletedAt(),
                report.getCreatedAt(),
                report.getUpdatedAt());
    }
}
