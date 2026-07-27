package com.A501.backend.domain.conversation.service;

import java.time.Duration;

import com.A501.backend.domain.conversation.dto.realtime.RealtimeTicketRecord;

/**
 * Realtime Ticket 상태를 {@code realtime:ticket:{jti}}에 저장한다. 원자적 일회용 소비는
 * FastAPI·W02가 담당하며, 여기서는 발급 시점의 {@code ISSUED} 기록만 담당한다.
 */
public interface RealtimeTicketStore {

    void saveIssued(String jti, RealtimeTicketRecord record, Duration ttl);
}
