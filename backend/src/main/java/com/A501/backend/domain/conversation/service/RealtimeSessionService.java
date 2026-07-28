package com.A501.backend.domain.conversation.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.common.util.ApiDateTimeMapper;
import com.A501.backend.domain.conversation.dto.realtime.ContextSnapshot;
import com.A501.backend.domain.conversation.dto.realtime.RealtimeSessionLeaseRecord;
import com.A501.backend.domain.conversation.dto.realtime.RealtimeTicketRecord;
import com.A501.backend.domain.conversation.dto.response.RealtimeSessionResponse;
import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.exception.ConversationErrorCode;
import com.A501.backend.domain.conversation.exception.ConversationException;
import com.A501.backend.domain.conversation.repository.ConversationSessionRepository;

import lombok.RequiredArgsConstructor;

/**
 * Realtime Session(Context Snapshot·Ticket·Lease) 발급을 오케스트레이션한다. 이 Work
 * Package(W01)는 초기 발급만 다루며, Ticket 일회용 소비·Heartbeat·Lease 만료 정리는 W02가
 * 담당한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RealtimeSessionService {

    private static final String REALTIME_SESSION_ID_PREFIX = "rt_";

    private final ConversationSessionRepository conversationSessionRepository;
    private final ChildAccessVerifier childAccessVerifier;
    private final CurrentMemberProvider currentMemberProvider;
    private final ContextSnapshotBuilder contextSnapshotBuilder;
    private final RealtimeContextStore realtimeContextStore;
    private final RealtimeContextProperties realtimeContextProperties;
    private final RealtimeTicketProvider realtimeTicketProvider;
    private final RealtimeTicketStore realtimeTicketStore;
    private final RealtimeTicketProperties realtimeTicketProperties;
    private final RealtimeSessionLeaseStore realtimeSessionLeaseStore;
    private final RealtimeSessionProperties realtimeSessionProperties;
    @Qualifier("utcClock")
    private final Clock utcClock;

    @Transactional
    public RealtimeSessionResponse issue(String conversationId) {
        ConversationSession session = conversationSessionRepository.findById(conversationId)
                .orElseThrow(() -> new ConversationException(ConversationErrorCode.CONVERSATION_NOT_FOUND));
        verifyConversationAccess(session);
        if (session.getStatus().isTerminal()) {
            throw new ConversationException(ConversationErrorCode.INVALID_SESSION_STATE);
        }

        LocalDateTime epochStartedAt =
                LocalDateTime.now(utcClock).truncatedTo(ChronoUnit.MICROS);
        int sessionEpoch = session.startRealtimeSession(epochStartedAt);
        String realtimeSessionId = generateRealtimeSessionId();

        ContextSnapshot snapshot = contextSnapshotBuilder.build(session);
        realtimeContextStore.save(conversationId, snapshot, realtimeContextProperties.ttl());

        RealtimeTicketIssueResult ticket = realtimeTicketProvider.issue(
                conversationId, realtimeSessionId, sessionEpoch);
        realtimeTicketStore.saveIssued(
                ticket.jti(),
                RealtimeTicketRecord.issued(conversationId, realtimeSessionId, sessionEpoch),
                realtimeTicketProperties.ttl());

        realtimeSessionLeaseStore.initialize(
                conversationId,
                RealtimeSessionLeaseRecord.active(
                        realtimeSessionId, sessionEpoch, utcClock.instant().toEpochMilli()),
                realtimeSessionProperties.leaseTtl());

        return RealtimeSessionResponse.of(
                conversationId,
                realtimeSessionId,
                sessionEpoch,
                realtimeSessionProperties.signalingUrl(),
                ticket.ticket(),
                ApiDateTimeMapper.utcToKst(ticket.expiresAt()),
                realtimeSessionProperties.iceServers());
    }

    private void verifyConversationAccess(ConversationSession session) {
        try {
            childAccessVerifier.verifyAccess(
                    currentMemberProvider.getCurrentMemberId(), session.getChildId());
        } catch (ConversationException exception) {
            if (exception.getErrorCode() == ConversationErrorCode.CHILD_ACCESS_DENIED) {
                throw new ConversationException(ConversationErrorCode.CONVERSATION_ACCESS_DENIED);
            }
            if (exception.getErrorCode() == ConversationErrorCode.CHILD_NOT_FOUND) {
                throw new ConversationException(ConversationErrorCode.CONVERSATION_NOT_FOUND);
            }
            throw exception;
        }
    }

    private String generateRealtimeSessionId() {
        return REALTIME_SESSION_ID_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }
}
