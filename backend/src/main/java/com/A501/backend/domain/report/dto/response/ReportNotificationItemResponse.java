package com.A501.backend.domain.report.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.A501.backend.domain.report.entity.ReportNotification;
import com.A501.backend.domain.report.entity.ReportNotificationType;

public record ReportNotificationItemResponse(
        Long notificationId,
        Long reportId,
        Long childId,
        LocalDate reportDate,
        ReportNotificationType type,
        String message,
        LocalDateTime availableAt
) {
    public static ReportNotificationItemResponse from(
            ReportNotification notification) {
        return new ReportNotificationItemResponse(
                notification.getId(),
                notification.getReportId(),
                notification.getChildId(),
                notification.getReportDate(),
                notification.getNotificationType(),
                notification.getMessage(),
                notification.getScheduledAt());
    }
}
