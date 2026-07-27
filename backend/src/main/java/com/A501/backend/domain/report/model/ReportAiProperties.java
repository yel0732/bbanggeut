package com.A501.backend.domain.report.model;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "report.ai")
public record ReportAiProperties(
        boolean enabled,
        String baseUrl,
        Duration timeout
) {
    public ReportAiProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://127.0.0.1:8000";
        }
        if (timeout == null) {
            timeout = Duration.ofSeconds(12);
        } else if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Report AI timeout must be positive.");
        }
    }
}
