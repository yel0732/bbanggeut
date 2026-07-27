package com.A501.backend.domain.report.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.report.entity.DailyReport;
import com.A501.backend.domain.report.entity.ReportGenerationStatus;
import com.A501.backend.domain.report.entity.ReportNotification;
import com.A501.backend.domain.report.entity.ReportNotificationType;
import com.A501.backend.domain.report.event.ReportNotificationCreatedEvent;
import com.A501.backend.domain.report.port.ReportNotificationRecipientPort;
import com.A501.backend.domain.report.port.ReportNotificationRecipientPort.ReportNotificationRecipient;
import com.A501.backend.domain.report.repository.DailyReportRepository;
import com.A501.backend.domain.report.repository.ReportNotificationRepository;

@Service
public class ReportNotificationPersistenceService {

    private final DailyReportRepository dailyReportRepository;
    private final ReportNotificationRepository notificationRepository;
    private final ObjectProvider<ReportNotificationRecipientPort>
            recipientPortProvider;
    private final ApplicationEventPublisher eventPublisher;

    public ReportNotificationPersistenceService(
            DailyReportRepository dailyReportRepository,
            ReportNotificationRepository notificationRepository,
            ObjectProvider<ReportNotificationRecipientPort>
                    recipientPortProvider,
            ApplicationEventPublisher eventPublisher
    ) {
        this.dailyReportRepository = dailyReportRepository;
        this.notificationRepository = notificationRepository;
        this.recipientPortProvider = recipientPortProvider;
        this.eventPublisher = eventPublisher;
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
        ReportNotificationRecipient recipient =
                recipientPort.findRecipient(report.getChildId());

        ReportNotification saved = notificationRepository.saveAndFlush(
                ReportNotification.create(
                        recipient.parentId(),
                        report.getId(),
                        report.getChildId(),
                        report.getReportDate(),
                        notificationType,
                        messageOf(notificationType, recipient.childName())));

        // 저장 트랜잭션(REQUIRES_NEW) 커밋 이후에만 SSE 전송이 일어나도록
        // AFTER_COMMIT 리스너용 이벤트만 발행한다. 실제 전송은 리스너가 맡는다.
        eventPublisher.publishEvent(new ReportNotificationCreatedEvent(
                saved.getId(),
                saved.getParentId(),
                saved.getNotificationType(),
                saved.getReportDate(),
                saved.getMessage()));
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

    private String messageOf(
            ReportNotificationType notificationType,
            String childName
    ) {
        return notificationType == ReportNotificationType.REPORT_COMPLETED
                ? childName + "의 일일 리포트가 준비되었습니다."
                : childName + "의 일일 리포트를 생성하지 못했습니다. 다시 생성할 수 있습니다.";
    }
}
