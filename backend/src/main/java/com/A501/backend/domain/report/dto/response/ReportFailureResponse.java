package com.A501.backend.domain.report.dto.response;

import java.time.LocalDateTime;

import com.A501.backend.domain.report.entity.DailyReport;
import com.A501.backend.domain.report.entity.ReportGenerationAttempt;
import com.A501.backend.domain.report.entity.ReportGenerationStatus;

public record ReportFailureResponse(
        String code,
        String message,
        int retryCount,
        boolean canRegenerate,
        LocalDateTime failedAt
) {

    public static ReportFailureResponse from(
            DailyReport report,
            ReportGenerationAttempt lastAttempt
    ) {
        if (report.getGenerationStatus() != ReportGenerationStatus.FAILED) {
            return null;
        }
        String code = lastAttempt != null
                && lastAttempt.getErrorCode() != null
                ? lastAttempt.getErrorCode().name()
                : "UNKNOWN_GENERATION_FAILURE";
        return new ReportFailureResponse(
                code,
                report.getFailureReason(),
                report.getRetryCount(),
                true,
                report.getFailedAt());
    }
}
