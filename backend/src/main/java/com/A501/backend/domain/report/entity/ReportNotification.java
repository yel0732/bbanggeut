package com.A501.backend.domain.report.entity;

import java.time.LocalDate;

import com.A501.backend.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "report_notification",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_report_notification_report_type",
                        columnNames = {"report_id", "notification_type"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportNotification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    @Column(name = "parent_id", nullable = false)
    private Long parentId;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "child_id", nullable = false)
    private Long childId;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 30)
    private ReportNotificationType notificationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_status", nullable = false, length = 20)
    private ReportNotificationStatus notificationStatus;

    @Column(name = "message", nullable = false, length = 200)
    private String message;

    private ReportNotification(
            Long parentId,
            Long reportId,
            Long childId,
            LocalDate reportDate,
            ReportNotificationType notificationType,
            String message
    ) {
        if (parentId == null || reportId == null || childId == null) {
            throw new IllegalArgumentException(
                    "Parent, report, and child IDs are required.");
        }
        if (reportDate == null || notificationType == null) {
            throw new IllegalArgumentException(
                    "Report date and notification type are required.");
        }
        if (message == null || message.isBlank() || message.length() > 200) {
            throw new IllegalArgumentException(
                    "Notification message must contain 1 to 200 characters.");
        }
        this.parentId = parentId;
        this.reportId = reportId;
        this.childId = childId;
        this.reportDate = reportDate;
        this.notificationType = notificationType;
        this.notificationStatus = ReportNotificationStatus.UNREAD;
        this.message = message;
    }

    public static ReportNotification create(
            Long parentId,
            Long reportId,
            Long childId,
            LocalDate reportDate,
            ReportNotificationType notificationType,
            String message
    ) {
        return new ReportNotification(
                parentId,
                reportId,
                childId,
                reportDate,
                notificationType,
                message);
    }
}
