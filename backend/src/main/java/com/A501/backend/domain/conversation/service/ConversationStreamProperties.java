package com.A501.backend.domain.conversation.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "conversation.stream")
public record ConversationStreamProperties(
        String key,
        String group,
        boolean enabled,
        Duration pollInterval,
        int batchSize,
        Recovery recovery) {

    public ConversationStreamProperties {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Conversation Stream key is required.");
        }
        if (group == null || group.isBlank()) {
            throw new IllegalArgumentException("Conversation Stream consumer group is required.");
        }
        if (pollInterval == null || pollInterval.isNegative() || pollInterval.isZero()) {
            throw new IllegalArgumentException("Conversation Stream poll interval must be positive.");
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("Conversation Stream batch size must be positive.");
        }
        if (recovery == null) {
            throw new IllegalArgumentException("Conversation Stream recovery settings are required.");
        }
    }

    public record Recovery(
            boolean enabled,
            Duration interval,
            Duration initialDelay,
            Duration minIdle,
            int maxAttempts,
            int batchSize) {

        public Recovery {
            if (interval == null || interval.isNegative() || interval.isZero()) {
                throw new IllegalArgumentException("Conversation Stream recovery interval must be positive.");
            }
            if (initialDelay == null || initialDelay.isNegative()) {
                throw new IllegalArgumentException("Conversation Stream recovery initial delay cannot be negative.");
            }
            if (minIdle == null || minIdle.isNegative() || minIdle.isZero()) {
                throw new IllegalArgumentException("Conversation Stream pending minimum idle must be positive.");
            }
            if (maxAttempts < 1 || batchSize < 1) {
                throw new IllegalArgumentException("Conversation Stream recovery counts must be positive.");
            }
        }
    }
}
