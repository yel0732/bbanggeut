package com.A501.backend.domain.conversation.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.conversation.repository.ConversationSessionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 회원 탈퇴·자녀 삭제 시 conversation 도메인 데이터를 물리 삭제한다.
 *
 * <p>기존 {@link ConversationContentPurger}를 그대로 재사용한다. 직접 bulk delete를 짜면
 * Redis 정리(realtime context·session lease)를 빠뜨리기 쉬운데, 재사용하면 그 위험이 없다.
 * 삭제 문장 수는 대화 세션 수만큼 늘지만 행 삭제 비용은 동일하고, 백그라운드 배치라
 * 왕복 비용이 문제되지 않는다. 오히려 통짜 IN 절보다 트랜잭션이 잘게 나뉘어 락 점유가 짧다.
 *
 * <p>{@code ConversationContentPurger}는 세션 행을 "미완료 시도" 흔적으로 남기지만,
 * 파기에서는 그 흔적도 지워야 하므로 마지막에 세션까지 삭제한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationPurgeService {

    private final ConversationSessionRepository sessionRepository;
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

        for (String conversationId : conversationIds) {
            contentPurger.purge(conversationId);
        }
        int deletedSessions = sessionRepository.deleteByChildIds(childIds);

        // 대화 내용은 로그에 남기지 않는다. 건수만 남긴다.
        log.info("대화 데이터 hard delete 완료: sessions={}", deletedSessions);
    }
}
