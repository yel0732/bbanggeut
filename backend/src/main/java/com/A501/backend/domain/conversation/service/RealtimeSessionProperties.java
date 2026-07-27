package com.A501.backend.domain.conversation.service;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "conversation.realtime.session")
public record RealtimeSessionProperties(
        String signalingUrl,
        List<String> iceServers,
        Duration leaseTtl) {

    public RealtimeSessionProperties {
        if (signalingUrl == null || signalingUrl.isBlank()) {
            throw new IllegalArgumentException("Realtime Signaling URL이 필요합니다.");
        }
        iceServers = iceServers == null ? List.of() : List.copyOf(iceServers);
        if (leaseTtl == null || leaseTtl.isZero() || leaseTtl.isNegative()) {
            throw new IllegalArgumentException("Session Lease TTL은 양수여야 합니다.");
        }
    }
}
