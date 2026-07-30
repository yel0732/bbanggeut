package com.A501.backend.domain.report.dto.response;

import java.util.List;

public record UnreadReportNotificationsResponse(
        int unreadCount,
        List<ReportNotificationItemResponse> notifications
) {
    public UnreadReportNotificationsResponse {
        notifications = List.copyOf(notifications);
    }
}
