package com.A501.backend.domain.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.refresh-token")
public record RefreshTokenProperties(long rememberMeExpiryDays, long defaultExpiryDays) {
}
