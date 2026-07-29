package com.A501.backend.domain.report.batch.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.A501.backend.domain.report.service.ReportNotificationDeliveryService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ReportNotificationDeliveryScheduler {

    private final ReportNotificationDeliveryService deliveryService;

    @Scheduled(
            fixedDelayString = "${report.notification.delivery-interval:PT30S}",
            initialDelayString = "${report.notification.delivery-initial-delay:PT10S}")
    public void deliverDueNotifications() {
        deliveryService.deliverDue();
    }
}
