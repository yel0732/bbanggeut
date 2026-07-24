package com.A501.backend.domain.report.dto.response;

public record ReportDataAvailabilityResponse(
        boolean hasGameData,
        boolean hasConversationData
) {
}
