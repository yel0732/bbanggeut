package com.A501.backend.domain.child.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.child")
public record ChildProperties(int maxProfileCount) {
}
