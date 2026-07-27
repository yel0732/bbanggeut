package com.A501.backend.domain.childdiary.model;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "child-diary.preview")
public record ChildDiaryPreviewProperties(String secret, Duration ttl) {
    public ChildDiaryPreviewProperties {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("Child diary preview secret must contain at least 32 characters.");
        }
        if (ttl == null) ttl = Duration.ofMinutes(10);
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Child diary preview TTL must be positive.");
        }
    }
}
