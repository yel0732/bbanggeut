package com.A501.backend.domain.conversation.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "conversation.realtime.context")
public record RealtimeContextProperties(Duration ttl) {

    public RealtimeContextProperties {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Context Snapshot TTL은 양수여야 합니다.");
        }
    }
}
