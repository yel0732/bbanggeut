package com.A501.backend.domain.report.model;

import java.time.LocalTime;
import java.time.ZoneId;

public record ReportDeliverySchedule(
        LocalTime deliveryTime,
        String timezone
) {
    public ReportDeliverySchedule {
        if (deliveryTime == null || timezone == null || timezone.isBlank()) {
            throw new IllegalArgumentException(
                    "Report delivery time and timezone are required.");
        }
        deliveryTime = deliveryTime.withSecond(0).withNano(0);
        ZoneId.of(timezone);
    }
}
