package com.A501.backend.domain.report.model;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "report.retry")
public record ReportRetryProperties(
        int maxAttempts,
        Duration interval
) {

    public ReportRetryProperties {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException(
                    "Report retry max attempts must be at least 1.");
        }
        if (interval == null || interval.isNegative()) {
            throw new IllegalArgumentException(
                    "Report retry interval must not be negative.");
        }
    }
}
