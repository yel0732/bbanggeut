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
import com.A501.backend.domain.conversation.entity.ConversationTopic;
import com.A501.backend.domain.conversation.entity.ConversationTopicState;
import com.A501.backend.domain.conversation.entity.ConversationTopicStatus;
import com.A501.backend.domain.conversation.entity.TerminationReason;
import com.A501.backend.domain.conversation.exception.ConversationErrorCode;
import com.A501.backend.domain.conversation.exception.ConversationException;
import com.A501.backend.domain.conversation.repository.ConversationGameSessionRepository;
import com.A501.backend.domain.conversation.repository.ConversationSessionRepository;
import com.A501.backend.domain.conversation.repository.ConversationTopicStateRepository;
import com.A501.backend.domain.conversation.repository.ConversationTurnRepository;

@ExtendWith(MockitoExtension.class)
class ConversationTerminationExecutorTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-22T01:08:00Z"), ZoneOffset.UTC);

    @Mock
    private ConversationSessionRepository conversationSessionRepository;

    @Mock
    private ConversationTopicStateRepository topicStateRepository;

    @Mock
    private ConversationTurnRepository turnRepository;

    @Mock
    private ConversationGameSessionRepository gameSessionRepository;

    @Mock
    private RealtimeContextStore realtimeContextStore;

    @Mock
    private RealtimeSessionLeaseStore realtimeSessionLeaseStore;

    private ConversationTerminationExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new ConversationTerminationExecutor(
                conversationSessionRepository,
                topicStateRepository,
                turnRepository,
                gameSessionRepository,
                realtimeContextStore,
                realtimeSessionLeaseStore,
                FIXED_CLOCK);
    }

    @Test
    void 완료_종료는_UTC_Clock으로_상태와_종료_정보를_저장하고_원문을_보존한다() {
        ConversationSession session = startedSession("conv_executor_001");
        ConversationTopicState topic = dailyTopic("conv_executor_001");
        topic.start(LocalDateTime.of(2026, 7, 22, 1, 0));
        when(conversationSessionRepository.findById("conv_executor_001")).thenReturn(Optional.of(session));
        when(topicStateRepository.findByConversationIdAndTopicTypeForUpdate(
                "conv_executor_001",
                ConversationTopic.DAILY)).thenReturn(Optional.of(topic));
        when(conversationSessionRepository.saveAndFlush(session)).thenReturn(session);

        ConversationSession result = executor.terminate(
                "conv_executor_001", TerminationReason.USER_FINISHED);

        assertThat(result.getStatus()).isEqualTo(ConversationStatus.COMPLETED);
        assertThat(result.getCompletedAt()).isEqualTo(LocalDateTime.of(2026, 7, 22, 1, 8));
        assertThat(result.getTerminationReason()).isEqualTo(TerminationReason.USER_FINISHED);
        assertThat(topic.getStatus()).isEqualTo(ConversationTopicStatus.COMPLETED);
        assertThat(topic.getCompletionReason()).isEqualTo(TerminationReason.USER_FINISHED);
        verify(conversationSessionRepository).saveAndFlush(session);
        // 완료 세션의 원문은 일기에 쓰이므로 삭제하지 않는다.
        verify(turnRepository, never()).deleteByConversationId(any());
        verify(topicStateRepository, never()).deleteByConversationId(any());
        verify(gameSessionRepository, never()).deleteByConversationId(any());
        verify(realtimeContextStore, never()).delete(any());
        verify(realtimeSessionLeaseStore, never()).delete(any());
    }

    @Test
    void 연결_끊김_종료는_세션은_ABORTED로_남기고_대화_원문을_정리한다() {
        ConversationSession session = startedSession("conv_executor_004");
        when(conversationSessionRepository.findById("conv_executor_004")).thenReturn(Optional.of(session));
        when(conversationSessionRepository.saveAndFlush(session)).thenReturn(session);

        ConversationSession result = executor.terminate(
                "conv_executor_004", TerminationReason.CONNECTION_LOST);

        assertThat(result.getStatus()).isEqualTo(ConversationStatus.ABORTED);
        assertThat(result.getTerminationReason()).isEqualTo(TerminationReason.CONNECTION_LOST);
        verify(turnRepository).deleteByConversationId("conv_executor_004");
        verify(topicStateRepository).deleteByConversationId("conv_executor_004");
        verify(gameSessionRepository).deleteByConversationId("conv_executor_004");
        verify(realtimeContextStore).delete("conv_executor_004");
        verify(realtimeSessionLeaseStore).delete("conv_executor_004");
        // 정리 경로에서는 현재 토픽을 완료 처리하지 않는다(어차피 삭제).
        verify(topicStateRepository, never())
                .findByConversationIdAndTopicTypeForUpdate(any(), any());
    }

    @Test
    void 사용자_명시_이탈_종료도_대화_원문을_정리한다() {
        ConversationSession session = startedSession("conv_executor_005");
        when(conversationSessionRepository.findById("conv_executor_005")).thenReturn(Optional.of(session));
        when(conversationSessionRepository.saveAndFlush(session)).thenReturn(session);

        ConversationSession result = executor.terminate(
                "conv_executor_005", TerminationReason.USER_ABANDONED);

        assertThat(result.getStatus()).isEqualTo(ConversationStatus.ABORTED);
        verify(turnRepository).deleteByConversationId("conv_executor_005");
        verify(topicStateRepository).deleteByConversationId("conv_executor_005");
        verify(gameSessionRepository).deleteByConversationId("conv_executor_005");
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

    private ConversationTopicState dailyTopic(String conversationId) {
        return ConversationTopicState.create(
                "topic_" + conversationId,
                conversationId,
                ConversationTopic.DAILY,
                1,
                6,
                "{}",
                "{\"topic\":\"DAILY\",\"games\":[]}");
    }
}
