package com.A501.backend.domain.conversation.service;

import java.time.Duration;

import com.A501.backend.domain.conversation.dto.realtime.RealtimeSessionLeaseRecord;

/**
 * Session Lease를 {@code realtime:session:{conversationId}}에 저장한다. Heartbeat 갱신과
 * 만료 정리는 W02(Reconciler)가 담당하며, 여기서는 발급 시점의 초기 상태만 담당한다.
 */
public interface RealtimeSessionLeaseStore {

    void initialize(String conversationId, RealtimeSessionLeaseRecord record, Duration ttl);
}
