package com.A501.backend.domain.conversation.service;

import java.time.Duration;

import com.A501.backend.domain.conversation.dto.realtime.ContextSnapshot;

/**
 * Context Snapshot을 {@code realtime:context:{conversationId}}에 저장한다. FastAPI는
 * 이 저장소를 직접 읽으며, Spring은 매 턴 재조회 요청을 받지 않는다(ADR-008).
 */
public interface RealtimeContextStore {

    void save(String conversationId, ContextSnapshot snapshot, Duration ttl);

    /** 대화가 이탈·중단으로 정리될 때 Context Snapshot(아이 프로필 포함)을 제거한다. */
    void delete(String conversationId);
}
