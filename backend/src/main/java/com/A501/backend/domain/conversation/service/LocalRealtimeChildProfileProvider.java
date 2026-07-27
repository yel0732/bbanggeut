package com.A501.backend.domain.conversation.service;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.A501.backend.domain.conversation.dto.realtime.RealtimeChildContext;

/**
 * child 도메인 연동 전 로컬 개발용 Mock 프로필. 실제 아이 개인정보를 사용하지 않고
 * 가명·더미 값만 반환한다.
 */
@Component
@Profile("local")
public class LocalRealtimeChildProfileProvider implements RealtimeChildProfileProvider {

    @Override
    public RealtimeChildContext getProfile(Long childId) {
        return new RealtimeChildContext("하준", "6_9", List.of("공룡", "블록"));
    }
}
