package com.A501.backend.domain.conversation.service;

import org.springframework.stereotype.Component;

import com.A501.backend.domain.conversation.repository.ConversationGameSessionRepository;
import com.A501.backend.domain.conversation.repository.ConversationTopicStateRepository;
import com.A501.backend.domain.conversation.repository.ConversationTurnRepository;

import lombok.RequiredArgsConstructor;

/**
 * 완주하지 못한 대화의 원문·슬롯·실시간 아티팩트를 정리한다.
 *
 * <p>세션 행 자체는 terminal 상태·종료 사유·시각을 담은 "미완료 시도" 흔적으로 남겨,
 * 지연 도착한 확정 이벤트가 정리된 원문을 되살리지 못하게 한다(terminal 가드).
 * Redis 티켓은 jti로만 조회되므로 짧은 TTL 만료에 맡긴다.
 *
 * <p>종료 경로가 둘(사용자·Reconciler가 부르는 {@link ConversationTerminationExecutor},
 * FastAPI 이벤트를 처리하는 {@link ConversationSessionEventHandler})이므로 정리 책임을
 * 한곳에 모은다. 호출자의 트랜잭션 안에서 실행된다.
 */
@Component
@RequiredArgsConstructor
public class ConversationContentPurger {

    private final ConversationTurnRepository turnRepository;
    private final ConversationTopicStateRepository topicStateRepository;
    private final ConversationGameSessionRepository gameSessionRepository;
    private final RealtimeContextStore realtimeContextStore;
    private final RealtimeSessionLeaseStore realtimeSessionLeaseStore;

    public void purge(String conversationId) {
        turnRepository.deleteByConversationId(conversationId);
        topicStateRepository.deleteByConversationId(conversationId);
        gameSessionRepository.deleteByConversationId(conversationId);
        realtimeContextStore.delete(conversationId);
        realtimeSessionLeaseStore.delete(conversationId);
    }
}
