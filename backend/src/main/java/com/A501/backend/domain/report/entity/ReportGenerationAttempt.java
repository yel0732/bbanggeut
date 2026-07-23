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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "report_generation_attempt",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_report_generation_attempt_number",
                        columnNames = {"report_id", "attempt_number"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportGenerationAttempt extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attempt_id")
    private Long id;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "attempt_status", nullable = false, length = 20)
    private ReportAttemptStatus attemptStatus;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_stage", length = 40)
    private ReportFailureStage failureStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_code", length = 60)
    private ReportErrorCode errorCode;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    private ReportGenerationAttempt(
            Long reportId,
            int attemptNumber,
            LocalDateTime startedAt
    ) {
        if (reportId == null || reportId < 1) {
            throw new IllegalArgumentException("Report ID must be at least 1.");
        }
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("Attempt number must be at least 1.");
        }
        if (startedAt == null) {
            throw new IllegalArgumentException("Attempt start time is required.");
        }
        this.reportId = reportId;
        this.attemptNumber = attemptNumber;
        this.attemptStatus = ReportAttemptStatus.PROCESSING;
        this.startedAt = startedAt;
    }

    public static ReportGenerationAttempt processing(
            Long reportId,
            int attemptNumber,
            LocalDateTime startedAt
    ) {
        return new ReportGenerationAttempt(reportId, attemptNumber, startedAt);
    }

    public void complete(LocalDateTime endedAt) {
        validateProcessingAndEndTime(endedAt);
        attemptStatus = ReportAttemptStatus.COMPLETED;
        this.endedAt = endedAt;
    }

    public void fail(
            LocalDateTime endedAt,
            ReportFailureStage failureStage,
            ReportErrorCode errorCode,
            String failureReason
    ) {
        validateProcessingAndEndTime(endedAt);
        if (failureStage == null || errorCode == null) {
            throw new IllegalArgumentException(
                    "Failure stage and error code are required.");
        }
        if (failureReason == null || failureReason.isBlank()) {
            throw new IllegalArgumentException("Failure reason is required.");
        }
        attemptStatus = ReportAttemptStatus.FAILED;
        this.endedAt = endedAt;
        this.failureStage = failureStage;
        this.errorCode = errorCode;
        this.failureReason = failureReason;
    }

    private void validateProcessingAndEndTime(LocalDateTime endedAt) {
        if (attemptStatus != ReportAttemptStatus.PROCESSING) {
            throw new IllegalStateException(
                    "Only a processing attempt can be finished.");
        }
        if (endedAt == null || endedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException(
                    "Attempt end time must not be before start time.");
        }
    }
}
