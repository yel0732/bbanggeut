package com.A501.backend.domain.report.model;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "report.notification.sse")
public record ReportNotificationSseProperties(
        Duration timeout,
        Duration heartbeatInterval
) {

    public ReportNotificationSseProperties {
        requirePositive(timeout, "SSE timeout");
        requirePositive(heartbeatInterval, "SSE heartbeat interval");
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(
                    name + " must be a positive duration.");
        }
    }
}
