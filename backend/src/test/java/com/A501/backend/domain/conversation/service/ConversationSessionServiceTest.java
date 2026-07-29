package com.A501.backend.domain.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.A501.backend.domain.conversation.dto.request.CompleteConversationRequest;
import com.A501.backend.domain.conversation.dto.response.CompleteConversationResponse;
import com.A501.backend.domain.conversation.dto.response.ConversationSessionResponse;
import com.A501.backend.domain.conversation.dto.response.ConversationTurnListResponse;
import com.A501.backend.domain.conversation.dto.response.CreateConversationResponse;
import com.A501.backend.domain.conversation.entity.ContextType;
import com.A501.backend.domain.conversation.entity.ConversationGoal;
import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.ConversationStatus;
import com.A501.backend.domain.conversation.entity.ConversationTopic;
import com.A501.backend.domain.conversation.entity.ConversationTopicState;
import com.A501.backend.domain.conversation.entity.ConversationTurn;
import com.A501.backend.domain.conversation.entity.Speaker;
import com.A501.backend.domain.conversation.entity.TerminationReason;
import com.A501.backend.domain.conversation.exception.ConversationErrorCode;
import com.A501.backend.domain.conversation.exception.ConversationException;
import com.A501.backend.domain.conversation.repository.ConversationGameSessionRepository;
import com.A501.backend.domain.conversation.repository.ConversationSessionRepository;
import com.A501.backend.domain.conversation.repository.ConversationTopicStateRepository;
import com.A501.backend.domain.conversation.repository.ConversationTurnRepository;
import com.A501.backend.domain.game.model.CompletedGameSessionSummary;
import com.A501.backend.domain.game.service.GameSessionService;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ConversationSessionServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-22T03:00:00Z"),
            ZoneOffset.UTC);

    @Mock
    private ConversationSessionRepository conversationSessionRepository;
    @Mock
    private ConversationTurnRepository conversationTurnRepository;
    @Mock
    private ConversationTopicStateRepository topicStateRepository;
    @Mock
    private ConversationGameSessionRepository conversationGameSessionRepository;
    @Mock
    private GameSessionService gameSessionService;
    @Mock
    private ChildAccessVerifier childAccessVerifier;
    @Mock
    private CurrentMemberProvider currentMemberProvider;
    @Mock
    private ConversationTerminationCoordinator conversationTerminationCoordinator;

    private ConversationSessionService service;

    @BeforeEach
    void setUp() {
        service = new ConversationSessionService(
                conversationSessionRepository,
                conversationTurnRepository,
                topicStateRepository,
                conversationGameSessionRepository,
                gameSessionService,
                childAccessVerifier,
                currentMemberProvider,
                conversationTerminationCoordinator,
                new ConversationTopicProperties(6, 6),
                new ObjectMapper(),
                CLOCK);
        when(currentMemberProvider.getCurrentMemberId()).thenReturn(7L);
    }

    @Test
    void createsDailyOnlyPlanWhenTodayHasNoGames() {
        when(gameSessionService.findCompletedSessionsForChild(
                eq(15L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(conversationSessionRepository.save(any(ConversationSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateConversationResponse response = service.create(15L);

        assertThat(response.status()).isEqualTo(ConversationStatus.READY);
        assertThat(response.currentTopic()).isEqualTo(ConversationTopic.DAILY);
        assertThat(response.topics()).singleElement().satisfies(topic -> {
            assertThat(topic.type()).isEqualTo(ConversationTopic.DAILY);
            assertThat(topic.questionCount()).isZero();
            assertThat(topic.maxQuestionCount()).isEqualTo(6);
        });
        verify(topicStateRepository).saveAll(any());
        verify(conversationGameSessionRepository, never()).saveAll(any());
    }

    @Test
    void createsOneGameTopicForMultipleTodayGamesThenDaily() {
        List<CompletedGameSessionSummary> games = List.of(
                gameSummary(1L, 10),
                gameSummary(2L, 20));
        when(gameSessionService.findCompletedSessionsForChild(
                eq(15L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(games);
        when(conversationSessionRepository.save(any(ConversationSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateConversationResponse response = service.create(15L);

        assertThat(response.currentTopic()).isEqualTo(ConversationTopic.GAME);
        assertThat(response.topics())
                .extracting(topic -> topic.type())
                .containsExactly(ConversationTopic.GAME, ConversationTopic.DAILY);
        verify(conversationGameSessionRepository).saveAll(any());
    }

    @Test
    void usesNaiveKstDayBoundariesForGameLookup() {
        // completed_at은 KST wall-clock으로 저장되므로 조회 경계도 naive KST 자정이어야 한다.
        when(gameSessionService.findCompletedSessionsForChild(
                eq(15L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        service.create(15L);

        ArgumentCaptor<LocalDateTime> startedAt =
                ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endedAt =
                ArgumentCaptor.forClass(LocalDateTime.class);
        verify(gameSessionService).findCompletedSessionsForChild(
                eq(15L), startedAt.capture(), endedAt.capture());
        assertThat(startedAt.getValue())
                .isEqualTo(LocalDateTime.of(2026, 7, 22, 0, 0));
        assertThat(endedAt.getValue())
                .isEqualTo(LocalDateTime.of(2026, 7, 23, 0, 0));
    }

    @Test
    void eveningConversationStillSeesSameKstDayGames() {
        // KST 21시(= 12:00Z)에 대화를 시작해도 조회 창은 같은 KST 날짜여야 한다.
        // 과거 UTC 변환 방식에서는 이 시각에 20시(KST) 완료 게임이 누락됐다.
        ConversationSessionService eveningService = new ConversationSessionService(
                conversationSessionRepository,
                conversationTurnRepository,
                topicStateRepository,
                conversationGameSessionRepository,
                gameSessionService,
                childAccessVerifier,
                currentMemberProvider,
                conversationTerminationCoordinator,
                new ConversationTopicProperties(6, 6),
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-07-22T12:00:00Z"), ZoneOffset.UTC));
        when(gameSessionService.findCompletedSessionsForChild(
                eq(15L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        eveningService.create(15L);

        ArgumentCaptor<LocalDateTime> startedAt =
                ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endedAt =
                ArgumentCaptor.forClass(LocalDateTime.class);
        verify(gameSessionService).findCompletedSessionsForChild(
                eq(15L), startedAt.capture(), endedAt.capture());
        assertThat(startedAt.getValue())
                .isEqualTo(LocalDateTime.of(2026, 7, 22, 0, 0));
        assertThat(endedAt.getValue())
                .isEqualTo(LocalDateTime.of(2026, 7, 23, 0, 0));
        // 20:00(KST) 완료 게임이 창 안에 들어온다.
        assertThat(LocalDateTime.of(2026, 7, 22, 20, 0))
                .isAfterOrEqualTo(startedAt.getValue())
                .isBefore(endedAt.getValue());
    }

    @Test
    void rejectsDuplicateActiveConversationBeforeReadingGames() {
        when(conversationSessionRepository.existsByChildIdAndStatusIn(
                eq(15L), anyCollection()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(15L))
                .isInstanceOfSatisfying(
                        ConversationException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        ConversationErrorCode.ACTIVE_CONVERSATION_EXISTS));

        verify(gameSessionService, never()).findCompletedSessionsForChild(
                anyLong(), any(), any());
    }

    @Test
    void stopsWhenChildAccessVerificationFails() {
        doThrow(new ConversationException(ConversationErrorCode.CHILD_ACCESS_DENIED))
                .when(childAccessVerifier)
                .verifyAccess(7L, 15L);

        assertThatThrownBy(() -> service.create(15L))
                .isInstanceOfSatisfying(
                        ConversationException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ConversationErrorCode.CHILD_ACCESS_DENIED));

        verify(conversationSessionRepository, never())
                .existsByChildIdAndStatusIn(anyLong(), anyCollection());
    }

    @Test
    void returnsSessionWithTopicPlanAndKstTimes() {
        ConversationSession session = dailySession("conv_query_001");
        session.start();
        session.terminate(
                LocalDateTime.of(2026, 7, 22, 1, 8),
                TerminationReason.USER_FINISHED);
        ReflectionTestUtils.setField(session, "questionCount", 3);
        ReflectionTestUtils.setField(
                session,
                "startedAt",
                LocalDateTime.of(2026, 7, 22, 1, 0, 10));
        ConversationTopicState topic = topic(
                "conv_query_001",
                ConversationTopic.DAILY,
                1);
        when(conversationSessionRepository.findById("conv_query_001"))
                .thenReturn(Optional.of(session));
        when(topicStateRepository.findAllByConversationIdOrderByTopicOrderAsc(
                "conv_query_001"))
                .thenReturn(List.of(topic));

        ConversationSessionResponse response =
                service.getConversation("conv_query_001");

        assertThat(response.totalQuestionCount()).isEqualTo(3);
        assertThat(response.currentTopic()).isEqualTo(ConversationTopic.DAILY);
        assertThat(response.topics()).hasSize(1);
        assertThat(response.startedAt()).isEqualTo(OffsetDateTime.of(
                2026, 7, 22, 10, 0, 10, 0, ZoneOffset.ofHours(9)));
    }

    @Test
    void completesActiveConversation() {
        ConversationSession active = startedSession("conv_complete_001");
        ConversationSession completed = startedSession("conv_complete_001");
        completed.terminate(
                LocalDateTime.of(2026, 7, 22, 1, 8),
                TerminationReason.USER_FINISHED);
        when(conversationSessionRepository.findById("conv_complete_001"))
                .thenReturn(Optional.of(active));
        when(conversationTerminationCoordinator.terminate(
                "conv_complete_001",
                TerminationReason.USER_FINISHED))
                .thenReturn(completed);

        CompleteConversationResponse response = service.completeConversation(
                "conv_complete_001",
                new CompleteConversationRequest("USER_FINISHED"));

        // 종료 버튼으로 끝낸 대화는 완주가 아니므로 ABORTED로 남는다.
        assertThat(response.status()).isEqualTo(ConversationStatus.ABORTED);
        assertThat(response.terminationReason())
                .isEqualTo(TerminationReason.USER_FINISHED);
    }

    @Test
    void rejectsClientDeclaredCompletionReason() {
        ConversationSession active = startedSession("conv_complete_002");
        when(conversationSessionRepository.findById("conv_complete_002"))
                .thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.completeConversation(
                "conv_complete_002",
                new CompleteConversationRequest("REQUIRED_SLOTS_FILLED")))
                .isInstanceOfSatisfying(ConversationException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ConversationErrorCode.INVALID_TERMINATION_REASON));
    }

    @Test
    void listsTurnsInSequence() {
        ConversationSession session = dailySession("conv_turns_001");
        ConversationTurn childTurn = ConversationTurn.createChildCompleted(
                "turn_002",
                "conv_turns_001",
                2,
                "재미있었어요",
                new BigDecimal("0.9300"),
                LocalDateTime.of(2026, 7, 22, 1, 1),
                LocalDateTime.of(2026, 7, 22, 1, 1, 3));
        PageRequest pageable = PageRequest.of(0, 50);
        when(conversationSessionRepository.findById("conv_turns_001"))
                .thenReturn(Optional.of(session));
        when(conversationTurnRepository
                .findByConversationIdOrderByTurnSeqAsc(
                        "conv_turns_001",
                        pageable))
                .thenReturn(new PageImpl<>(List.of(childTurn), pageable, 1));

        ConversationTurnListResponse response =
                service.getConversationTurns("conv_turns_001", 0, 50, null);

        assertThat(response.turns()).singleElement().satisfies(turn -> {
            assertThat(turn.turnSeq()).isEqualTo(2);
            assertThat(turn.speaker()).isEqualTo(Speaker.CHILD);
            assertThat(turn.topicType()).isEqualTo(ConversationTopic.DAILY);
        });
    }

    private CompletedGameSessionSummary gameSummary(Long gameId, int score) {
        return new CompletedGameSessionSummary(
                UUID.randomUUID(),
                gameId,
                score,
                60,
                LocalDateTime.of(2026, 7, 22, 2, 0));
    }

    private ConversationTopicState topic(
            String conversationId,
            ConversationTopic type,
            int order) {
        return ConversationTopicState.create(
                "topic_" + order,
                conversationId,
                type,
                order,
                6,
                "{\"people\":[]}",
                "{\"topic\":\"" + type + "\",\"games\":[]}");
    }

    private ConversationSession dailySession(String conversationId) {
        return ConversationSession.create(
                conversationId,
                15L,
                null,
                ContextType.DAILY,
                ConversationGoal.DIARY,
                6);
    }

    private ConversationSession startedSession(String conversationId) {
        ConversationSession session = dailySession(conversationId);
        session.start();
        return session;
    }

    @SuppressWarnings("unchecked")
    private static Collection<ConversationStatus> anyCollection() {
        return any(Collection.class);
    }
}
