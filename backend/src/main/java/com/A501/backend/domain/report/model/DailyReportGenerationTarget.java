package com.A501.backend.domain.report.model;

import java.time.LocalDateTime;

public record DailyReportGenerationTarget(
        Long reportId,
        Long childId,
        LocalDateTime startedAt,
        LocalDateTime endedAt
) {
}
