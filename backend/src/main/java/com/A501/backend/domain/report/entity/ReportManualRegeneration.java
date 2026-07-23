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
        name = "report_manual_regeneration",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_report_manual_regeneration_parent_key",
                        columnNames = {"parent_id", "idempotency_key"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportManualRegeneration extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "regeneration_id")
    private Long id;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "child_id", nullable = false)
    private Long childId;

    @Column(name = "parent_id", nullable = false)
    private Long parentId;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "regeneration_status", nullable = false, length = 20)
    private ManualRegenerationStatus regenerationStatus;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "previous_failure_reason", length = 1000)
    private String previousFailureReason;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    private ReportManualRegeneration(
            Long reportId,
            Long childId,
            Long parentId,
            String idempotencyKey,
            LocalDateTime requestedAt,
            String previousFailureReason
    ) {
        this.reportId = reportId;
        this.childId = childId;
        this.parentId = parentId;
        this.idempotencyKey = idempotencyKey;
        this.regenerationStatus = ManualRegenerationStatus.PENDING;
        this.requestedAt = requestedAt;
        this.previousFailureReason = previousFailureReason;
    }

    public static ReportManualRegeneration pending(
            Long reportId,
            Long childId,
            Long parentId,
            String idempotencyKey,
            LocalDateTime requestedAt,
            String previousFailureReason
    ) {
        if (reportId == null || childId == null || parentId == null) {
            throw new IllegalArgumentException(
                    "Report, child, and parent IDs are required.");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.length() > 100) {
            throw new IllegalArgumentException(
                    "Idempotency key must contain 1 to 100 characters.");
        }
        if (requestedAt == null) {
            throw new IllegalArgumentException("Requested time is required.");
        }
        return new ReportManualRegeneration(
                reportId,
                childId,
                parentId,
                idempotencyKey,
                requestedAt,
                previousFailureReason);
    }

    public void start(LocalDateTime startedAt) {
        if (regenerationStatus != ManualRegenerationStatus.PENDING) {
            throw new IllegalStateException(
                    "Only a pending regeneration can be started.");
        }
        this.regenerationStatus = ManualRegenerationStatus.PROCESSING;
        this.startedAt = startedAt;
    }

    public void complete(LocalDateTime endedAt) {
        validateProcessing(endedAt);
        this.regenerationStatus = ManualRegenerationStatus.COMPLETED;
        this.endedAt = endedAt;
    }

    public void fail(LocalDateTime endedAt, String failureReason) {
        validateProcessing(endedAt);
        this.regenerationStatus = ManualRegenerationStatus.FAILED;
        this.endedAt = endedAt;
        this.failureReason = failureReason;
    }

    private void validateProcessing(LocalDateTime endedAt) {
        if (regenerationStatus != ManualRegenerationStatus.PROCESSING) {
            throw new IllegalStateException(
                    "Only a processing regeneration can be finished.");
        }
        if (endedAt == null || endedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException(
                    "Regeneration end time must not be before start time.");
        }
    }
}
