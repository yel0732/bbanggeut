package com.A501.backend.domain.report.event;

public record ReportGenerationRequestedEvent(Long reportId) {

    public ReportGenerationRequestedEvent {
        if (reportId == null || reportId < 1) {
            throw new IllegalArgumentException("Report ID must be positive.");
        }
    }
}
