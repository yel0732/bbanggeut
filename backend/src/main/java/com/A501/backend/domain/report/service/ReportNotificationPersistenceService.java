package com.A501.backend.domain.report.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.report.entity.DailyReport;
import com.A501.backend.domain.report.entity.ReportGenerationStatus;
import com.A501.backend.domain.report.entity.ReportNotification;
import com.A501.backend.domain.report.entity.ReportNotificationType;
import com.A501.backend.domain.report.port.ReportNotificationRecipientPort;
import com.A501.backend.domain.report.repository.DailyReportRepository;
import com.A501.backend.domain.report.repository.ReportNotificationRepository;

@Service
public class ReportNotificationPersistenceService {

    static final String COMPLETED_MESSAGE =
            "새로운 일일 리포트가 준비되었습니다.";
    static final String FAILED_MESSAGE =
            "일일 리포트를 생성하지 못했습니다. 다시 생성할 수 있습니다.";

    private final DailyReportRepository dailyReportRepository;
    private final ReportNotificationRepository notificationRepository;
    private final ObjectProvider<ReportNotificationRecipientPort>
            recipientPortProvider;

    public ReportNotificationPersistenceService(
            DailyReportRepository dailyReportRepository,
            ReportNotificationRepository notificationRepository,
            ObjectProvider<ReportNotificationRecipientPort>
                    recipientPortProvider
    ) {
        this.dailyReportRepository = dailyReportRepository;
        this.notificationRepository = notificationRepository;
        this.recipientPortProvider = recipientPortProvider;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void create(
            Long reportId,
            ReportNotificationType notificationType
    ) {
        DailyReport report = dailyReportRepository.findById(reportId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Daily report not found."));
        validateTerminalStatus(report, notificationType);
        if (notificationRepository.existsByReportIdAndNotificationType(
                reportId,
                notificationType)) {
            return;
        }

        ReportNotificationRecipientPort recipientPort =
                recipientPortProvider.getIfAvailable();
        if (recipientPort == null) {
            throw new IllegalStateException(
                    "ReportNotificationRecipientPort is not connected.");
        }
        Long parentId = recipientPort.findParentId(report.getChildId());

        notificationRepository.saveAndFlush(ReportNotification.create(
                parentId,
                report.getId(),
                report.getChildId(),
                report.getReportDate(),
                notificationType,
                messageOf(notificationType)));
    }

    private void validateTerminalStatus(
            DailyReport report,
            ReportNotificationType notificationType
    ) {
        ReportGenerationStatus expectedStatus =
                notificationType == ReportNotificationType.REPORT_COMPLETED
                        ? ReportGenerationStatus.COMPLETED
                        : ReportGenerationStatus.FAILED;
        if (report.getGenerationStatus() != expectedStatus) {
            throw new IllegalStateException(
                    "Report status does not match notification type.");
        }
    }

    private String messageOf(ReportNotificationType notificationType) {
        return notificationType == ReportNotificationType.REPORT_COMPLETED
                ? COMPLETED_MESSAGE
                : FAILED_MESSAGE;
    }
}
