package com.A501.backend.domain.report.entity;

import java.time.LocalDateTime;

import com.A501.backend.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "report_notification_failure")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportNotificationFailure extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_failure_id")
    private Long id;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 30)
    private ReportNotificationType notificationType;

    @Column(name = "failed_at", nullable = false)
    private LocalDateTime failedAt;

    @Column(name = "failure_reason", nullable = false, length = 1000)
    private String failureReason;

    private ReportNotificationFailure(
            Long reportId,
            ReportNotificationType notificationType,
            LocalDateTime failedAt,
            String failureReason
    ) {
        this.reportId = reportId;
        this.notificationType = notificationType;
        this.failedAt = failedAt;
        this.failureReason = failureReason;
    }

    public static ReportNotificationFailure record(
            Long reportId,
            ReportNotificationType notificationType,
            LocalDateTime failedAt,
            String failureReason
    ) {
        if (reportId == null || notificationType == null || failedAt == null) {
            throw new IllegalArgumentException(
                    "Report, notification type, and failed time are required.");
        }
        if (failureReason == null || failureReason.isBlank()
                || failureReason.length() > 1000) {
            throw new IllegalArgumentException(
                    "Failure reason must contain 1 to 1000 characters.");
        }
        return new ReportNotificationFailure(
                reportId,
                notificationType,
                failedAt,
                failureReason);
    }
}
