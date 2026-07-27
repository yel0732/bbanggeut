package com.A501.backend.domain.conversation.service;

import java.time.Duration;
import java.util.Optional;

import com.A501.backend.domain.conversation.dto.realtime.RealtimeSessionLeaseRecord;

/**
 * Session Lease를 {@code realtime:session:{conversationId}}에 저장한다. 초기화는 W01에서,
 * Heartbeat 갱신은 FastAPI가 직접 Redis를 다뤄 담당한다. {@link #find}는 Reconciler(T08)가
 * Lease 생존 여부를 확인하는 데 사용한다.
 */
public interface RealtimeSessionLeaseStore {

    void initialize(String conversationId, RealtimeSessionLeaseRecord record, Duration ttl);

    Optional<RealtimeSessionLeaseRecord> find(String conversationId);
}
