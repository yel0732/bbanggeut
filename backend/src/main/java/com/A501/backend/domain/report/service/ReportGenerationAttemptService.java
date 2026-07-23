package com.A501.backend.domain.report.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.report.entity.ReportGenerationAttempt;
import com.A501.backend.domain.report.model.ReportGenerationFailure;
import com.A501.backend.domain.report.repository.ReportGenerationAttemptRepository;

@Service
public class ReportGenerationAttemptService {

    private static final int MAX_FAILURE_REASON_LENGTH = 1000;

    private final ReportGenerationAttemptRepository attemptRepository;
    private final Clock clock;

    public ReportGenerationAttemptService(
            ReportGenerationAttemptRepository attemptRepository,
            @Qualifier("utcClock") Clock clock
    ) {
        this.attemptRepository = attemptRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long startAttempt(Long reportId) {
        int attemptNumber =
                attemptRepository.findMaxAttemptNumberByReportId(reportId) + 1;
        ReportGenerationAttempt attempt = ReportGenerationAttempt.processing(
                reportId,
                attemptNumber,
                now());
        return attemptRepository.saveAndFlush(attempt).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(
            Long attemptId,
            ReportGenerationFailure failure
    ) {
        ReportGenerationAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Report generation attempt not found."));
        attempt.fail(
                now(),
                failure.stage(),
                failure.errorCode(),
                failureReasonOf(failure.originalException()));
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
