package com.A501.backend.domain.conversation.service;

import org.springframework.stereotype.Component;

import com.A501.backend.domain.conversation.dto.realtime.RealtimeChildContext;

import lombok.RequiredArgsConstructor;

/**
 * Context Snapshot에 실을 아이 프로필을 child 테이블에서 읽는다.
 *
 * <p>소유권 검증과 별도 빈으로 둔다 — 검증만 대체하고 프로필은 실물을 쓰는 테스트가
 * 있어서, 한 빈이 두 인터페이스를 겸하면 그 대체가 성립하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class JdbcRealtimeChildProfileProvider implements RealtimeChildProfileProvider {

    private final ConversationChildDirectory childDirectory;

    @Override
    public RealtimeChildContext getProfile(Long childId) {
        return new RealtimeChildContext(childDirectory.find(childId).name());
    }
}
