package com.A501.backend.domain.report.model;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "report.recovery")
public record ReportRecoveryProperties(
        boolean enabled,
        Duration staleProcessingThreshold,
        int batchSize
) {

    public ReportRecoveryProperties {
        if (staleProcessingThreshold == null
                || staleProcessingThreshold.isNegative()
                || staleProcessingThreshold.isZero()) {
            throw new IllegalArgumentException(
                    "Report stale processing threshold must be positive.");
        }
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException(
                    "Report recovery batch size must be between 1 and 1000.");
        }
    }
}
