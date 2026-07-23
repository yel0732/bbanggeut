package com.A501.backend.domain.report.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.report.entity.DailyReport;
import com.A501.backend.domain.report.model.DailyReportGenerationTarget;
import com.A501.backend.domain.report.repository.DailyReportRepository;

@Service
public class DailyReportStatusService {

    private static final int MAX_FAILURE_REASON_LENGTH = 1000;

    private final DailyReportRepository dailyReportRepository;
    private final Clock clock;

    public DailyReportStatusService(
            DailyReportRepository dailyReportRepository,
            @Qualifier("utcClock") Clock clock
    ) {
        this.dailyReportRepository = dailyReportRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DailyReportGenerationTarget startProcessing(Long reportId) {
        DailyReport report = findForUpdate(reportId);
        report.startProcessing(now());

        return new DailyReportGenerationTarget(
                report.getId(),
                report.getChildId(),
                report.getPeriodStartedAt(),
                report.getPeriodEndedAt());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long reportId, RuntimeException exception) {
        DailyReport report = findForUpdate(reportId);
        report.fail(now(), failureReasonOf(exception));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRetry(Long reportId) {
        DailyReport report = findForUpdate(reportId);
        report.recordRetry();
    }

    private DailyReport findForUpdate(Long reportId) {
        return dailyReportRepository.findByIdForUpdate(reportId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Daily report not found."));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private String failureReasonOf(RuntimeException exception) {
        String message = exception.getMessage();
        String reason = message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
        reason = reason.replaceAll("[\\r\\n\\t]+", " ").trim();
        return reason.length() <= MAX_FAILURE_REASON_LENGTH
                ? reason
                : reason.substring(0, MAX_FAILURE_REASON_LENGTH);
    }
}
