package com.A501.backend.domain.conversation.service;

import java.time.LocalDateTime;

/**
 * {@link RealtimeTicketProvider#issue}의 결과. {@code ticket}은 로그에 남기지 않는다.
 */
public record RealtimeTicketIssueResult(String ticket, String jti, LocalDateTime expiresAt) {

    public RealtimeTicketIssueResult {
        if (ticket == null || ticket.isBlank()) {
            throw new IllegalArgumentException("서명된 Ticket이 필요합니다.");
        }
        if (jti == null || jti.isBlank()) {
            throw new IllegalArgumentException("Ticket jti가 필요합니다.");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("Ticket 만료 시각이 필요합니다.");
        }
    }
}
