package com.A501.backend.domain.conversation.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "conversation.ai")
public record DialogueAiProperties(
        String baseUrl,
        Duration timeout) {

    public DialogueAiProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("AI 서버 URL이 필요합니다.");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("AI 서버 Timeout은 양수여야 합니다.");
        }
    }
}
