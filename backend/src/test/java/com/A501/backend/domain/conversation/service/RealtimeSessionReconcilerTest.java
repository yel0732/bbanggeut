package com.A501.backend.domain.conversation.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.A501.backend.domain.conversation.dto.realtime.RealtimeSessionLeaseRecord;
import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.ConversationTopic;
import com.A501.backend.domain.conversation.entity.TerminationReason;
import com.A501.backend.domain.conversation.exception.ConversationErrorCode;
import com.A501.backend.domain.conversation.exception.ConversationException;
import com.A501.backend.domain.conversation.repository.ConversationSessionRepository;

@ExtendWith(MockitoExtension.class)
class RealtimeSessionReconcilerTest {

    @Mock
    private ConversationSessionRepository conversationSessionRepository;

    @Mock
    private RealtimeSessionLeaseStore realtimeSessionLeaseStore;

    @Mock
    private ConversationTerminationCoordinator conversationTerminationCoordinator;

    private RealtimeSessionReconciler reconciler;

    @BeforeEach
    void setUp() {
        reconciler = new RealtimeSessionReconciler(
                conversationSessionRepository,
                realtimeSessionLeaseStore,
                conversationTerminationCoordinator);
    }

    @Test
    void READY_세션의_Lease가_없으면_EXPIRED로_종료한다() {
        ConversationSession session = ConversationSession.create(
                "conv_ready", 15L, ConversationTopic.DAILY, 6);
        session.startRealtimeSession();
        when(conversationSessionRepository
                .findAllByStatusInAndCurrentSessionEpochGreaterThan(any(), eq(0)))
                .thenReturn(List.of(session));
        when(realtimeSessionLeaseStore.find("conv_ready")).thenReturn(Optional.empty());

        reconciler.reconcile();

        verify(conversationTerminationCoordinator)
                .terminate("conv_ready", TerminationReason.INACTIVITY_TIMEOUT);
    }

    @Test
    void IN_PROGRESS_세션의_Lease가_없으면_ABORTED로_종료한다() {
        ConversationSession session = ConversationSession.create(
                "conv_progress", 15L, ConversationTopic.DAILY, 6);
        session.startRealtimeSession();
        session.start();
        when(conversationSessionRepository
                .findAllByStatusInAndCurrentSessionEpochGreaterThan(any(), eq(0)))
                .thenReturn(List.of(session));
        when(realtimeSessionLeaseStore.find("conv_progress")).thenReturn(Optional.empty());

        reconciler.reconcile();

        verify(conversationTerminationCoordinator)
                .terminate("conv_progress", TerminationReason.CONNECTION_LOST);
    }

    @Test
    void Lease가_아직_있으면_종료하지_않는다() {
        ConversationSession session = ConversationSession.create(
                "conv_alive", 15L, ConversationTopic.DAILY, 6);
        session.startRealtimeSession();
        when(conversationSessionRepository
                .findAllByStatusInAndCurrentSessionEpochGreaterThan(any(), eq(0)))
                .thenReturn(List.of(session));
        when(realtimeSessionLeaseStore.find("conv_alive"))
                .thenReturn(Optional.of(RealtimeSessionLeaseRecord.active("rt_1", 1, 1_000L)));

        reconciler.reconcile();

        verify(conversationTerminationCoordinator, never()).terminate(any(), any());
    }

    @Test
    void 한_세션_처리가_실패해도_나머지_세션은_계속_처리한다() {
        ConversationSession failing = ConversationSession.create(
                "conv_fail", 15L, ConversationTopic.DAILY, 6);
        failing.startRealtimeSession();
        ConversationSession ok = ConversationSession.create(
                "conv_ok", 15L, ConversationTopic.DAILY, 6);
        ok.startRealtimeSession();
        when(conversationSessionRepository
                .findAllByStatusInAndCurrentSessionEpochGreaterThan(any(), eq(0)))
                .thenReturn(List.of(failing, ok));
        when(realtimeSessionLeaseStore.find("conv_fail"))
                .thenThrow(new ConversationException(ConversationErrorCode.REDIS_UNAVAILABLE));
        when(realtimeSessionLeaseStore.find("conv_ok")).thenReturn(Optional.empty());

        reconciler.reconcile();

        verify(conversationTerminationCoordinator)
                .terminate("conv_ok", TerminationReason.INACTIVITY_TIMEOUT);
        verify(conversationTerminationCoordinator, never()).terminate(eq("conv_fail"), any());
    }

    @Test
    void 대상이_없으면_아무것도_종료하지_않는다() {
        when(conversationSessionRepository
                .findAllByStatusInAndCurrentSessionEpochGreaterThan(any(), eq(0)))
                .thenReturn(List.of());

        reconciler.reconcile();

        verify(conversationTerminationCoordinator, never()).terminate(any(), any());
    }
}
