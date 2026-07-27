package com.A501.backend.domain.report.model;

public record ReportAiGameInput(
        String gameName,
        int playDurationSeconds,
        int score
) {
}
