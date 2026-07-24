package com.A501.backend.domain.report.service;

import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;

import com.A501.backend.domain.report.entity.ReportNotificationType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportNotificationPublisher {

    private final ReportNotificationPersistenceService persistenceService;
    private final ReportNotificationFailureService failureService;

    public void publishCompleted(Long reportId) {
        publish(reportId, ReportNotificationType.REPORT_COMPLETED);
    }

    public void publishFinalFailure(Long reportId) {
        publish(reportId, ReportNotificationType.REPORT_FAILED);
    }

    private void publish(
            Long reportId,
            ReportNotificationType notificationType
    ) {
        try {
            persistenceService.create(reportId, notificationType);
        } catch (DataIntegrityViolationException duplicateException) {
            log.debug(
                    "Duplicate report notification was ignored. "
                            + "reportId={}, type={}",
                    reportId,
                    notificationType);
        } catch (RuntimeException notificationException) {
            log.warn(
                    "Report notification creation failed. reportId={}, type={}",
                    reportId,
                    notificationType,
                    notificationException);
            recordFailureSafely(
                    reportId,
                    notificationType,
                    notificationException);
        }
    }

    private void recordFailureSafely(
            Long reportId,
            ReportNotificationType notificationType,
            RuntimeException notificationException
    ) {
        try {
            failureService.record(
                    reportId,
                    notificationType,
                    notificationException);
        } catch (RuntimeException historyException) {
            log.error(
                    "Report notification failure history could not be saved. "
                            + "reportId={}, type={}",
                    reportId,
                    notificationType,
                    historyException);
        }
    }
}
