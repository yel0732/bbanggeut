package com.A501.backend.domain.report.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.report.entity.ManualRegenerationStatus;
import com.A501.backend.domain.report.repository.ReportManualRegenerationRepository;

@Service
public class ReportManualRegenerationHistoryService {

    private static final int MAX_FAILURE_REASON_LENGTH = 1000;

    private final ReportManualRegenerationRepository repository;
    private final Clock clock;

    public ReportManualRegenerationHistoryService(
            ReportManualRegenerationRepository repository,
            @Qualifier("utcClock") Clock clock
    ) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessing(Long reportId) {
        repository
                .findFirstByReportIdAndRegenerationStatusOrderByRequestedAtDesc(
                        reportId,
                        ManualRegenerationStatus.PENDING)
                .ifPresent(regeneration -> regeneration.start(now()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(Long reportId) {
        repository
                .findFirstByReportIdAndRegenerationStatusOrderByRequestedAtDesc(
                        reportId,
                        ManualRegenerationStatus.PROCESSING)
                .ifPresent(regeneration -> regeneration.complete(now()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long reportId, RuntimeException exception) {
        repository
                .findFirstByReportIdAndRegenerationStatusOrderByRequestedAtDesc(
                        reportId,
                        ManualRegenerationStatus.PROCESSING)
                .ifPresent(regeneration ->
                        regeneration.fail(now(), failureReasonOf(exception)));
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
