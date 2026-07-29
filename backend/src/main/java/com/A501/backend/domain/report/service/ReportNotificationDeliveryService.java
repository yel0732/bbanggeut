package com.A501.backend.domain.report.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.report.entity.ReportNotification;
import com.A501.backend.domain.report.event.ReportNotificationCreatedEvent;
import com.A501.backend.domain.report.repository.ReportNotificationRepository;

@Service
public class ReportNotificationDeliveryService {

    private final ReportNotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ReportNotificationDeliveryService(
            ReportNotificationRepository notificationRepository,
            ApplicationEventPublisher eventPublisher,
            @Qualifier("utcClock") Clock clock
    ) {
        this.notificationRepository = notificationRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public int deliverDue() {
        LocalDateTime now = LocalDateTime.now(clock);
        var due = notificationRepository
                .findTop100ByDeliveredAtIsNullAndScheduledAtLessThanEqualOrderByScheduledAtAsc(now);
        for (ReportNotification notification : due) {
            notification.markDelivered(now);
            eventPublisher.publishEvent(toEvent(notification));
        }
        return due.size();
    }

    private ReportNotificationCreatedEvent toEvent(
            ReportNotification notification
    ) {
        return new ReportNotificationCreatedEvent(
                notification.getId(),
                notification.getParentId(),
                notification.getNotificationType(),
                notification.getReportDate(),
                notification.getMessage());
    }
}
