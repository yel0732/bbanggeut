package com.A501.backend.domain.report.dto.response;

import java.time.LocalDateTime;

import com.A501.backend.domain.report.entity.DailyReport;

public record ReportAggregationPeriodResponse(
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        String timezone
) {

    public static ReportAggregationPeriodResponse from(DailyReport report) {
        return new ReportAggregationPeriodResponse(
                report.getPeriodStartedAt(),
                report.getPeriodEndedAt(),
                "Asia/Seoul");
    }
}
