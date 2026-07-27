package com.A501.backend.domain.conversation.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code privateKeyPem}·{@code publicKeyPem}은 {@code local} 프로필에서 비워두면
 * {@link RealtimeTicketProvider}가 부팅 시 RS256 키페어를 자동 생성한다. {@code prod}
 * 프로필은 두 값을 환경변수로 필수 주입해야 한다(기본값 없음).
 */
@ConfigurationProperties(prefix = "conversation.realtime.ticket")
public record RealtimeTicketProperties(
        String issuer,
        String audience,
        Duration ttl,
        String privateKeyPem,
        String publicKeyPem) {

    public RealtimeTicketProperties {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("Realtime Ticket issuer가 필요합니다.");
        }
        if (audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("Realtime Ticket audience가 필요합니다.");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Realtime Ticket TTL은 양수여야 합니다.");
        }
    }
}
