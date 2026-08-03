package com.A501.backend.domain.report.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.A501.backend.domain.report.entity.ReportGenerationStatus;

public record ReportGenerationResponse(
        Long reportId,
        Long childId,
        LocalDate reportDate,
        LocalDateTime periodStartedAt,
        LocalDateTime periodEndedAt,
        ReportGenerationStatus status
) {
}
