package com.A501.backend.domain.auth.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.pin")
public record PinProperties(int maxFailedAttempts, Duration lockDuration) {
}
