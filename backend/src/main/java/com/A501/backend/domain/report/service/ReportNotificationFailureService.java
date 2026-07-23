package com.A501.backend.domain.report.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.report.entity.ReportNotificationFailure;
import com.A501.backend.domain.report.entity.ReportNotificationType;
import com.A501.backend.domain.report.repository.ReportNotificationFailureRepository;

@Service
public class ReportNotificationFailureService {

    private static final int MAX_FAILURE_REASON_LENGTH = 1000;

    private final ReportNotificationFailureRepository failureRepository;
    private final Clock clock;

    public ReportNotificationFailureService(
            ReportNotificationFailureRepository failureRepository,
            @Qualifier("utcClock") Clock clock
    ) {
        this.failureRepository = failureRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            Long reportId,
            ReportNotificationType notificationType,
            RuntimeException exception
    ) {
        failureRepository.save(ReportNotificationFailure.record(
                reportId,
                notificationType,
                LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS),
                failureReasonOf(exception)));
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
