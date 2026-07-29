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
 *
 * <p>재사용의 대가로 Redis 의존성이 따라온다. purge()가 마지막에 realtime 아티팩트를 지우기
 * 때문이다. <b>DB 파기가 Redis 가용성에 걸리면 안 되므로</b> 그 실패는 대화 단위로 삼킨다
 * (아래 주석 참고). 실제로 Redis 없이 돌렸을 때 계정 파기 전체가 매 주기 실패하는 것을 확인해
 * 넣은 방어다.
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

        int redisFailures = 0;
        for (String conversationId : conversationIds) {
            try {
                contentPurger.purge(conversationId);
            } catch (RuntimeException e) {
                // purge()는 DB 삭제를 먼저 끝낸 뒤 마지막에 Redis를 정리한다. 따라서 여기로 오는
                // 예외는 실질적으로 Redis 장애이고 DB 행은 이미 지워진 상태다.
                //
                // 이걸 삼키지 않으면 Redis가 죽어 있는 동안 계정 파기 전체가 매 주기 실패한다.
                // realtime 아티팩트는 TTL(conversation.realtime.context.ttl / lease-ttl)로
                // 자동 소멸하므로 남아도 무해하다. DB 파기가 Redis 가용성에 걸리는 쪽이 더 나쁘다.
                redisFailures++;
            }
        }
        int deletedSessions = sessionRepository.deleteByChildIds(childIds);

        // 대화 내용은 로그에 남기지 않는다. 건수만 남긴다.
        log.info("대화 데이터 hard delete 완료: sessions={}", deletedSessions);
        if (redisFailures > 0) {
            log.warn("realtime 아티팩트 정리를 건너뛴 대화 {}건 — TTL 만료에 맡긴다", redisFailures);
        }
    }
}
