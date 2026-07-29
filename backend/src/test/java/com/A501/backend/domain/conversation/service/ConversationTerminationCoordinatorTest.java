package com.A501.backend.domain.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.A501.backend.domain.conversation.entity.ContextType;
import com.A501.backend.domain.conversation.entity.ConversationGoal;
import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.ConversationStatus;
import com.A501.backend.domain.conversation.entity.TerminationReason;
import com.A501.backend.domain.conversation.exception.ConversationErrorCode;
import com.A501.backend.domain.conversation.exception.ConversationException;
import com.A501.backend.domain.conversation.repository.ConversationSessionRepository;

@ExtendWith(MockitoExtension.class)
class ConversationTerminationCoordinatorTest {

    @Mock
    private ConversationSessionRepository conversationSessionRepository;

    @Mock
    private ConversationTerminationExecutor conversationTerminationExecutor;

    private ConversationTerminationCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new ConversationTerminationCoordinator(
                conversationSessionRepository, conversationTerminationExecutor);
    }

    @Test
    void 낙관적_락_충돌_후_최신_세션이_terminal이면_최초_결과를_반환한다() {
        ConversationSession completed = completedSession(
                "conv_conflict_001", TerminationReason.MAX_QUESTION_REACHED);
        when(conversationTerminationExecutor.terminate(
                "conv_conflict_001", TerminationReason.USER_FINISHED))
                .thenThrow(new ObjectOptimisticLockingFailureException("conflict", null));
        when(conversationSessionRepository.findById("conv_conflict_001"))
                .thenReturn(Optional.of(completed));

        ConversationSession result = coordinator.terminate(
                "conv_conflict_001", TerminationReason.USER_FINISHED);

        assertThat(result.getStatus()).isEqualTo(ConversationStatus.COMPLETED);
        assertThat(result.getTerminationReason()).isEqualTo(TerminationReason.MAX_QUESTION_REACHED);
        verify(conversationTerminationExecutor).terminate(
                "conv_conflict_001", TerminationReason.USER_FINISHED);
    }

    @Test
    void 충돌_후_최신_세션이_active이면_한_번_재시도한다() {
        ConversationSession active = startedSession("conv_conflict_002");
        ConversationSession completed = completedSession(
                "conv_conflict_002", TerminationReason.USER_FINISHED);
        when(conversationTerminationExecutor.terminate(
                "conv_conflict_002", TerminationReason.USER_FINISHED))
                .thenThrow(new ObjectOptimisticLockingFailureException("conflict", null))
                .thenReturn(completed);
        when(conversationSessionRepository.findById("conv_conflict_002"))
                .thenReturn(Optional.of(active));

        ConversationSession result = coordinator.terminate(
                "conv_conflict_002", TerminationReason.USER_FINISHED);

        assertThat(result.getStatus()).isEqualTo(ConversationStatus.ABORTED);
        verify(conversationTerminationExecutor, times(2)).terminate(
                "conv_conflict_002", TerminationReason.USER_FINISHED);
    }

    @Test
    void 두_번_충돌하고_최신_세션도_active이면_INVALID_SESSION_STATE를_반환한다() {
        ConversationSession active = startedSession("conv_conflict_003");
        when(conversationTerminationExecutor.terminate(
                "conv_conflict_003", TerminationReason.USER_FINISHED))
                .thenThrow(new ObjectOptimisticLockingFailureException("conflict", null));
        when(conversationSessionRepository.findById("conv_conflict_003"))
                .thenReturn(Optional.of(active));

        assertThatThrownBy(() -> coordinator.terminate(
                "conv_conflict_003", TerminationReason.USER_FINISHED))
                .isInstanceOfSatisfying(ConversationException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ConversationErrorCode.INVALID_SESSION_STATE));

        verify(conversationTerminationExecutor, times(2)).terminate(
                "conv_conflict_003", TerminationReason.USER_FINISHED);
    }

    private ConversationSession completedSession(String conversationId, TerminationReason reason) {
        ConversationSession session = startedSession(conversationId);
        session.terminate(LocalDateTime.of(2026, 7, 22, 1, 8), reason);
        return session;
    }

    private ConversationSession startedSession(String conversationId) {
        ConversationSession session = ConversationSession.create(
                conversationId, 15L, null, ContextType.DAILY, ConversationGoal.DIARY, 6);
        session.start();
        return session;
    }
}
