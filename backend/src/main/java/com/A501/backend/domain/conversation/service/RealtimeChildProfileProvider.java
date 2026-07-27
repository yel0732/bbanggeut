package com.A501.backend.domain.conversation.service;

import com.A501.backend.domain.conversation.dto.realtime.RealtimeChildContext;

/**
 * Context Snapshot에 담을 아이 프로필 정보를 제공한다. 실제 조회는 child 도메인 소관이며,
 * 이 인터페이스는 그 경계까지만 정의한다.
 */
public interface RealtimeChildProfileProvider {

    RealtimeChildContext getProfile(Long childId);
}
