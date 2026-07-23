package com.A501.backend.domain.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.A501.backend.domain.conversation.entity.ContextType;
import com.A501.backend.domain.conversation.entity.ConversationGoal;
import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.ConversationStatus;
import com.A501.backend.domain.conversation.entity.TerminationReason;
import com.A501.backend.domain.conversation.exception.ConversationErrorCode;
import com.A501.backend.domain.conversation.exception.ConversationException;
import com.A501.backend.domain.conversation.repository.ConversationSessionRepository;

@ExtendWith(MockitoExtension.class)
class ConversationTerminationExecutorTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-22T01:08:00Z"), ZoneOffset.UTC);

    @Mock
    private ConversationSessionRepository conversationSessionRepository;

    private ConversationTerminationExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new ConversationTerminationExecutor(conversationSessionRepository, FIXED_CLOCK);
    }

    @Test
    void UTC_Clock으로_상태와_종료_정보를_함께_저장한다() {
        ConversationSession session = startedSession("conv_executor_001");
        when(conversationSessionRepository.findById("conv_executor_001")).thenReturn(Optional.of(session));
        when(conversationSessionRepository.saveAndFlush(session)).thenReturn(session);

        ConversationSession result = executor.terminate(
                "conv_executor_001", TerminationReason.USER_FINISHED);

        assertThat(result.getStatus()).isEqualTo(ConversationStatus.COMPLETED);
        assertThat(result.getCompletedAt()).isEqualTo(LocalDateTime.of(2026, 7, 22, 1, 8));
        assertThat(result.getTerminationReason()).isEqualTo(TerminationReason.USER_FINISHED);
        verify(conversationSessionRepository).saveAndFlush(session);
    }

    @Test
    void 이미_terminal이면_저장하지_않고_최초_결과를_반환한다() {
        ConversationSession session = startedSession("conv_executor_002");
        session.terminate(LocalDateTime.of(2026, 7, 22, 1, 7), TerminationReason.USER_FINISHED);
        when(conversationSessionRepository.findById("conv_executor_002")).thenReturn(Optional.of(session));

        ConversationSession result = executor.terminate(
                "conv_executor_002", TerminationReason.SYSTEM_ERROR);

        assertThat(result.getTerminationReason()).isEqualTo(TerminationReason.USER_FINISHED);
        assertThat(result.getCompletedAt()).isEqualTo(LocalDateTime.of(2026, 7, 22, 1, 7));
        verify(conversationSessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void 현재_상태에서_종료할_수_없으면_INVALID_SESSION_STATE를_반환한다() {
        ConversationSession ready = ConversationSession.create(
                "conv_executor_003", 15L, null, ContextType.DAILY, ConversationGoal.DIARY, 6);
        when(conversationSessionRepository.findById("conv_executor_003")).thenReturn(Optional.of(ready));

        assertThatThrownBy(() -> executor.terminate(
                "conv_executor_003", TerminationReason.USER_FINISHED))
                .isInstanceOfSatisfying(ConversationException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ConversationErrorCode.INVALID_SESSION_STATE));
        verify(conversationSessionRepository, never()).saveAndFlush(any());
    }

    private ConversationSession startedSession(String conversationId) {
        ConversationSession session = ConversationSession.create(
                conversationId, 15L, null, ContextType.DAILY, ConversationGoal.DIARY, 6);
        session.start();
        return session;
    }
}
