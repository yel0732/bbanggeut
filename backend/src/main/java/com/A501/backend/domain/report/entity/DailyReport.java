package com.A501.backend.domain.report.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.A501.backend.common.entity.BaseTimeEntity;
import com.A501.backend.domain.report.model.ReportPeriod;

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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "daily_report",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_daily_report_child_date",
                        columnNames = {"child_id", "report_date"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyReport extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long id;

    @Column(name = "child_id", nullable = false)
    private Long childId;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(name = "period_started_at", nullable = false)
    private LocalDateTime periodStartedAt;

    @Column(name = "period_ended_at", nullable = false)
    private LocalDateTime periodEndedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "generation_status", nullable = false, length = 20)
    private ReportGenerationStatus generationStatus;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Builder(access = AccessLevel.PRIVATE)
    private DailyReport(
            Long childId,
            LocalDate reportDate,
            LocalDateTime periodStartedAt,
            LocalDateTime periodEndedAt,
            ReportGenerationStatus generationStatus,
            int retryCount) {
        this.childId = childId;
        this.reportDate = reportDate;
        this.periodStartedAt = periodStartedAt;
        this.periodEndedAt = periodEndedAt;
        this.generationStatus = generationStatus;
        this.retryCount = retryCount;
    }

    public static DailyReport pending(Long childId, ReportPeriod period) {
        return DailyReport.builder()
                .childId(childId)
                .reportDate(period.reportDate())
                .periodStartedAt(period.startedAt())
                .periodEndedAt(period.endedAt())
                .generationStatus(ReportGenerationStatus.PENDING)
                .retryCount(0)
                .build();
    }

    public void startProcessing() {
        if (generationStatus == ReportGenerationStatus.COMPLETED) {
            throw new IllegalStateException("A completed report cannot be processed again.");
        }
        if (generationStatus == ReportGenerationStatus.PROCESSING) {
            throw new IllegalStateException("The report is already being processed.");
        }
        if (generationStatus == ReportGenerationStatus.FAILED) {
            retryCount++;
        }

        generationStatus = ReportGenerationStatus.PROCESSING;
    }

    public void complete() {
        if (generationStatus != ReportGenerationStatus.PROCESSING) {
            throw new IllegalStateException(
                    "Only a processing report can be completed.");
        }

        generationStatus = ReportGenerationStatus.COMPLETED;
    }

    public void fail() {
        if (generationStatus == ReportGenerationStatus.COMPLETED) {
            throw new IllegalStateException("A completed report cannot be failed.");
        }

        generationStatus = ReportGenerationStatus.FAILED;
    }
}
