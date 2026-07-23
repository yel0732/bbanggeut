package com.A501.backend.domain.report.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ReportPeriod(
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        LocalDate reportDate
) {
}
