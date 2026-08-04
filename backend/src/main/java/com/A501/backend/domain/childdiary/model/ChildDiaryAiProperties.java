package com.A501.backend.domain.childdiary.model;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "child-diary.ai")
public record ChildDiaryAiProperties(String baseUrl, Duration timeout) {
    public ChildDiaryAiProperties {
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "http://127.0.0.1:8000";
        if (timeout == null) timeout = Duration.ofSeconds(100);
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Child diary AI timeout must be positive.");
        }
    }
}
