package com.A501.backend.domain.conversation.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.conversation.exception.ConversationErrorCode;
import com.A501.backend.domain.conversation.exception.ConversationException;
import com.A501.backend.domain.conversation.repository.ConversationEventFailureRepository;
import com.A501.backend.domain.conversation.repository.ConversationEventRepository;
import com.A501.backend.domain.conversation.repository.ConversationSessionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationPurgeService {

    private final ConversationSessionRepository sessionRepository;
    private final ConversationEventRepository eventRepository;
    private final ConversationEventFailureRepository eventFailureRepository;
    private final ConversationContentPurger contentPurger;

    @Transactional
    public void purgeByChildIds(List<Long> childIds) {
        if (childIds == null || childIds.isEmpty()) {
            return;
        }

        List<String> conversationIds = sessionRepository.findIdsByChildIds(childIds);
        if (conversationIds.isEmpty()) {
            return;
        }

        int skippedRealtimeCleanups = 0;
        for (String conversationId : conversationIds) {
            try {
                contentPurger.purge(conversationId);
            } catch (ConversationException e) {
                // Redis 장애만 삼킨다. purge()는 DB 삭제 3건을 먼저 끝내고 마지막에 realtime
                // 아티팩트를 지우므로, 여기까지 온 REDIS_UNAVAILABLE은 DB가 이미 정리된 상태다.
                //
                // 삼키지 않으면 Redis가 죽어 있는 동안 파기 자체가 막힌다. 자녀 프로필 삭제는
                // 이 서비스를 동기로 호출하므로, 부모가 PIN까지 맞게 입력했는데 삭제가 실패하고
                // 트랜잭션이 롤백되어 아무것도 지워지지 않는다. 계정 파기(스케줄러)에서는 매 주기
                // 실패가 반복된다. realtime 아티팩트는 TTL로 소멸하므로 남아도 무해하다.
                //
                // DB 오류는 삼키지 않는다 — 그걸 넘기면 대화 내용이 남은 채 세션 행만 지워진다.
                if (e.getErrorCode() != ConversationErrorCode.REDIS_UNAVAILABLE) {
                    throw e;
                }
                skippedRealtimeCleanups++;
            }
        }
        eventFailureRepository.deleteByConversationIds(conversationIds);
        eventRepository.deleteByConversationIds(conversationIds);
        sessionRepository.deleteByConversationIds(conversationIds);

        if (skippedRealtimeCleanups > 0) {
            // 대화 내용은 로그에 남기지 않는다. 건수만 남긴다.
            log.warn("realtime 아티팩트 정리를 건너뛴 대화 {}건 — TTL 만료에 맡긴다",
                    skippedRealtimeCleanups);
        }
    }
}
