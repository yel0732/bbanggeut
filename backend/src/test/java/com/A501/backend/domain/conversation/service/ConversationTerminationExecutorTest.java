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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

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
import com.A501.backend.domain.conversation.repository.ConversationSessionRepository;
import com.A501.backend.domain.conversation.repository.ConversationTopicStateRepository;

@ExtendWith(MockitoExtension.class)
class ConversationTerminationExecutorTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-22T01:08:00Z"), ZoneOffset.UTC);

    @Mock
    private ConversationSessionRepository conversationSessionRepository;

    @Mock
    private ConversationTopicStateRepository topicStateRepository;

    @Mock
    private ConversationContentPurger conversationContentPurger;

    private ConversationTerminationExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new ConversationTerminationExecutor(
                conversationSessionRepository,
                topicStateRepository,
                conversationContentPurger,
                FIXED_CLOCK);
    }

    @Test
    void 완주한_대화는_상태와_종료_정보를_저장하고_원문을_보존한다() {
        ConversationSession session = startedSession("conv_executor_001");
        ConversationTopicState topic = dailyTopic("conv_executor_001");
        topic.start(LocalDateTime.of(2026, 7, 22, 1, 0));
        when(conversationSessionRepository.findById("conv_executor_001")).thenReturn(Optional.of(session));
        when(topicStateRepository.findByConversationIdAndTopicTypeForUpdate(
                "conv_executor_001",
                ConversationTopic.DAILY)).thenReturn(Optional.of(topic));
        when(conversationSessionRepository.saveAndFlush(session)).thenReturn(session);

        ConversationSession result = executor.terminate(
                "conv_executor_001", TerminationReason.REQUIRED_SLOTS_FILLED);

        assertThat(result.getStatus()).isEqualTo(ConversationStatus.COMPLETED);
        assertThat(result.getCompletedAt()).isEqualTo(LocalDateTime.of(2026, 7, 22, 1, 8));
        assertThat(result.getTerminationReason())
                .isEqualTo(TerminationReason.REQUIRED_SLOTS_FILLED);
        assertThat(topic.getStatus()).isEqualTo(ConversationTopicStatus.COMPLETED);
        assertThat(topic.getCompletionReason())
                .isEqualTo(TerminationReason.REQUIRED_SLOTS_FILLED);
        verify(conversationSessionRepository).saveAndFlush(session);
        // 완주한 대화의 원문은 일기·리포트의 재료이므로 삭제하지 않는다.
        verify(conversationContentPurger, never()).purge(any());
    }

    @MockitoSettings(strictness = Strictness.LENIENT)
    @ParameterizedTest(name = "{0} 종료는 {1} 상태로 남기고 대화 내용을 정리한다")
    @CsvSource({
            "USER_FINISHED,ABORTED",
            "USER_ABANDONED,ABORTED",
            "CHILD_REQUESTED_STOP,ABORTED",
            "CONNECTION_LOST,ABORTED",
            "PAGE_CLOSED,ABORTED",
            "INACTIVITY_TIMEOUT,EXPIRED",
            "SYSTEM_ERROR,FAILED",
    })
    void 완주하지_못한_대화는_사유와_무관하게_내용을_정리한다(
            TerminationReason reason,
            ConversationStatus expectedStatus) {
        String conversationId = "conv_purge_" + reason.name();
        ConversationSession session = startedSession(conversationId);
        when(conversationSessionRepository.findById(conversationId)).thenReturn(Optional.of(session));
        when(conversationSessionRepository.saveAndFlush(session)).thenReturn(session);

        ConversationSession result = executor.terminate(conversationId, reason);

        assertThat(result.getStatus()).isEqualTo(expectedStatus);
        assertThat(result.getTerminationReason()).isEqualTo(reason);
        verify(conversationContentPurger).purge(conversationId);
        // 정리 경로에서는 현재 주제를 완료 처리하지 않는다(어차피 삭제).
        verify(topicStateRepository, never())
                .findByConversationIdAndTopicTypeForUpdate(any(), any());
    }

    @Test
    void 이미_terminal이면_저장하지_않고_최초_결과를_반환한다() {
        ConversationSession session = startedSession("conv_executor_002");
        session.terminate(LocalDateTime.of(2026, 7, 22, 1, 7),
                TerminationReason.REQUIRED_SLOTS_FILLED);
        when(conversationSessionRepository.findById("conv_executor_002")).thenReturn(Optional.of(session));

        ConversationSession result = executor.terminate(
                "conv_executor_002", TerminationReason.SYSTEM_ERROR);

        assertThat(result.getTerminationReason())
                .isEqualTo(TerminationReason.REQUIRED_SLOTS_FILLED);
        assertThat(result.getCompletedAt()).isEqualTo(LocalDateTime.of(2026, 7, 22, 1, 7));
        verify(conversationSessionRepository, never()).saveAndFlush(any());
        verify(conversationContentPurger, never()).purge(any());
    }

    @Test
    void 시작하지_않은_대화는_완주로_종료할_수_없다() {
        ConversationSession ready = ConversationSession.create(
                "conv_executor_003", 15L, null, ContextType.DAILY, ConversationGoal.DIARY, 6);
        when(conversationSessionRepository.findById("conv_executor_003")).thenReturn(Optional.of(ready));

        assertThatThrownBy(() -> executor.terminate(
                "conv_executor_003", TerminationReason.REQUIRED_SLOTS_FILLED))
                .isInstanceOfSatisfying(ConversationException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ConversationErrorCode.INVALID_SESSION_STATE));
        verify(conversationSessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void 시작하지_않은_대화도_이탈로_정리할_수_있다() {
        ConversationSession ready = ConversationSession.create(
                "conv_executor_004", 15L, null, ContextType.DAILY, ConversationGoal.DIARY, 6);
        when(conversationSessionRepository.findById("conv_executor_004")).thenReturn(Optional.of(ready));
        when(conversationSessionRepository.saveAndFlush(ready)).thenReturn(ready);

        ConversationSession result = executor.terminate(
                "conv_executor_004", TerminationReason.PAGE_CLOSED);

        assertThat(result.getStatus()).isEqualTo(ConversationStatus.ABORTED);
        verify(conversationContentPurger).purge("conv_executor_004");
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
