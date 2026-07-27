package com.A501.backend.domain.report.model;

public record ReportAiSummaryResponse(
        String title,
        String body,
        String modelVersion,
        String promptVersion
) {
}
