package com.A501.backend.domain.conversation.service;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.ConversationStatus;
import com.A501.backend.domain.conversation.entity.TerminationReason;
import com.A501.backend.domain.conversation.repository.ConversationSessionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Session Lease가 Redis에서 사라졌는데도(Heartbeat 중단·TTL 만료) 세션이 계속
 * READY·IN_PROGRESS로 남는 경우를 정리한다(CONV-REQ-044). 아직 한 번도 연결되지 못한
 * READY 세션은 INACTIVITY_TIMEOUT(→EXPIRED)으로, 연결된 적 있는 IN_PROGRESS 세션은
 * CONNECTION_LOST(→ABORTED)로 구분한다. 기본 비활성 — 로컬 dev·테스트에서 의도치 않게
 * 세션을 정리하지 않도록 명시적으로 켜야 동작한다(DailyReportJobScheduler와 동일 관례).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "conversation.realtime.reconciler",
        name = "enabled",
        havingValue = "true")
public class RealtimeSessionReconciler {

    private static final Set<ConversationStatus> ACTIVE_STATUSES =
            EnumSet.of(ConversationStatus.READY, ConversationStatus.IN_PROGRESS);

    private final ConversationSessionRepository conversationSessionRepository;
    private final RealtimeSessionLeaseStore realtimeSessionLeaseStore;
    private final ConversationTerminationCoordinator conversationTerminationCoordinator;

    @Scheduled(fixedRateString = "${conversation.realtime.reconciler.interval}")
    public void reconcile() {
        List<ConversationSession> candidates =
                conversationSessionRepository
                        .findAllByStatusInAndCurrentSessionEpochGreaterThan(ACTIVE_STATUSES, 0);

        for (ConversationSession session : candidates) {
            try {
                reconcileOne(session);
            } catch (RuntimeException exception) {
                log.warn(
                        "Failed to reconcile realtime session conversationId={}",
                        session.getConversationId(),
                        exception);
            }
        }
    }

    private void reconcileOne(ConversationSession session) {
        if (realtimeSessionLeaseStore.find(session.getConversationId()).isPresent()) {
            return;
        }

        TerminationReason reason = session.getStatus() == ConversationStatus.IN_PROGRESS
                ? TerminationReason.CONNECTION_LOST
                : TerminationReason.INACTIVITY_TIMEOUT;
        log.info(
                "Reconciling expired realtime session conversationId={} status={} reason={}",
                session.getConversationId(),
                session.getStatus(),
                reason);
        conversationTerminationCoordinator.terminate(session.getConversationId(), reason);
    }
}
