package com.A501.backend.domain.conversation.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.ConversationStatus;
import com.A501.backend.domain.conversation.entity.TerminationReason;
import com.A501.backend.domain.conversation.exception.ConversationErrorCode;
import com.A501.backend.domain.conversation.exception.ConversationException;
import com.A501.backend.domain.conversation.repository.ConversationGameSessionRepository;
import com.A501.backend.domain.conversation.repository.ConversationSessionRepository;
import com.A501.backend.domain.conversation.repository.ConversationTopicStateRepository;
import com.A501.backend.domain.conversation.repository.ConversationTurnRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ConversationTerminationExecutor {

    // 이탈·중단으로 끝난 세션. 일기로 이어지지 않으므로 대화 원문을 보관하지 않는다.
    private static final Set<ConversationStatus> ABANDONED_STATUSES =
            EnumSet.of(ConversationStatus.ABORTED, ConversationStatus.EXPIRED);

    private final ConversationSessionRepository conversationSessionRepository;
    private final ConversationTopicStateRepository topicStateRepository;
    private final ConversationTurnRepository turnRepository;
    private final ConversationGameSessionRepository gameSessionRepository;
    private final RealtimeContextStore realtimeContextStore;
    private final RealtimeSessionLeaseStore realtimeSessionLeaseStore;
    private final Clock utcClock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ConversationSession terminate(String conversationId, TerminationReason reason) {
        ConversationSession session = conversationSessionRepository.findById(conversationId)
                .orElseThrow(() -> new ConversationException(ConversationErrorCode.CONVERSATION_NOT_FOUND));

        if (session.getStatus().isTerminal()) {
            return session;
        }

        LocalDateTime completedAt =
                LocalDateTime.now(utcClock).truncatedTo(ChronoUnit.MICROS);
        try {
            session.terminate(completedAt, reason);
        } catch (IllegalStateException exception) {
            throw new ConversationException(ConversationErrorCode.INVALID_SESSION_STATE);
        }
        ConversationSession terminated = conversationSessionRepository.saveAndFlush(session);

        if (ABANDONED_STATUSES.contains(terminated.getStatus())) {
            purgeConversationContent(conversationId);
        } else {
            try {
                topicStateRepository
                        .findByConversationIdAndTopicTypeForUpdate(
                                conversationId,
                                terminated.getCurrentTopic())
                        .ifPresent(topic -> topic.complete(completedAt, reason));
            } catch (IllegalStateException exception) {
                throw new ConversationException(ConversationErrorCode.INVALID_SESSION_STATE);
            }
        }

        return terminated;
    }

    /**
     * 이탈·중단된 대화의 원문·슬롯·실시간 아티팩트를 제거한다. 세션 행 자체는 terminal
     * 상태·종료 사유·시각을 담은 "미완료 시도" 흔적으로 남겨, 지연 이벤트가 도착해도
     * terminal 세션으로 폐기되도록 한다. Redis 티켓은 jti로만 조회되므로 짧은 TTL 만료에 맡긴다.
     */
    private void purgeConversationContent(String conversationId) {
        turnRepository.deleteByConversationId(conversationId);
        topicStateRepository.deleteByConversationId(conversationId);
        gameSessionRepository.deleteByConversationId(conversationId);
        realtimeContextStore.delete(conversationId);
        realtimeSessionLeaseStore.delete(conversationId);
    }
}
