package com.A501.backend.domain.report.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.A501.backend.domain.report.entity.DailyReport;
import com.A501.backend.domain.report.entity.ReportGenerationStatus;

public record ReportListItemResponse(
        Long reportId,
        LocalDate reportDate,
        ReportGenerationStatus status,
        ReportDataAvailabilityResponse dataAvailability,
        LocalDateTime generatedAt,
        LocalDateTime createdAt,
        ReportFailureResponse failure
) {

    public static ReportListItemResponse from(
            DailyReport report,
            ReportDataAvailabilityResponse dataAvailability
    ) {
        return new ReportListItemResponse(
                report.getId(),
                report.getReportDate(),
                report.getGenerationStatus(),
                dataAvailability,
                report.getCompletedAt(),
                report.getCreatedAt(),
                ReportFailureResponse.from(report, null));
    }
}
