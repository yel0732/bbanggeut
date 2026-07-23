package com.A501.backend.domain.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.A501.backend.domain.conversation.dto.request.CreateConversationRequest;
import com.A501.backend.domain.conversation.dto.response.CreateConversationResponse;
import com.A501.backend.domain.conversation.dto.response.ConversationSessionResponse;
import com.A501.backend.domain.conversation.entity.ContextType;
import com.A501.backend.domain.conversation.entity.ConversationGoal;
import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.ConversationStatus;
import com.A501.backend.domain.conversation.exception.ConversationErrorCode;
import com.A501.backend.domain.conversation.exception.ConversationException;
import com.A501.backend.domain.conversation.repository.ConversationSessionRepository;
import com.A501.backend.domain.game.service.GameSessionService;

@ExtendWith(MockitoExtension.class)
class ConversationSessionServiceTest {

    @Mock
    private ConversationSessionRepository conversationSessionRepository;

    @Mock
    private GameSessionService gameSessionService;

    @Mock
    private ChildAccessVerifier childAccessVerifier;

    @Mock
    private CurrentMemberProvider currentMemberProvider;

    private ConversationSessionService service;

    @BeforeEach
    void setUp() {
        service = new ConversationSessionService(
                conversationSessionRepository,
                gameSessionService,
                childAccessVerifier,
                currentMemberProvider);
        lenient().when(currentMemberProvider.getCurrentMemberId()).thenReturn(7L);
    }

    @Test
    void DAILY_세션을_READY_상태로_생성한다() {
        CreateConversationRequest request = dailyRequest(6);
        when(conversationSessionRepository.save(any(ConversationSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateConversationResponse response = service.create(15L, request);

        ArgumentCaptor<ConversationSession> captor = ArgumentCaptor.forClass(ConversationSession.class);
        verify(childAccessVerifier).verifyAccess(7L, 15L);
        verify(conversationSessionRepository).save(captor.capture());
        ConversationSession saved = captor.getValue();
        assertThat(saved.getConversationId()).startsWith("conv_").hasSize(37);
        assertThat(saved.getStatus()).isEqualTo(ConversationStatus.READY);
        assertThat(saved.getQuestionCount()).isZero();
        assertThat(saved.getCurrentSessionEpoch()).isZero();
        assertThat(response.status()).isEqualTo(ConversationStatus.READY);
        assertThat(response.childId()).isEqualTo(15L);
    }

    @Test
    void GAME_세션은_같은_아이의_게임_세션을_검증하고_생성한다() {
        UUID gameSessionId = UUID.randomUUID();
        CreateConversationRequest request = new CreateConversationRequest(
                ContextType.GAME, gameSessionId, ConversationGoal.DIARY, 5);
        when(gameSessionService.existsForChild(gameSessionId, 15L)).thenReturn(true);
        when(conversationSessionRepository.save(any(ConversationSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateConversationResponse response = service.create(15L, request);

        assertThat(response.gameSessionId()).isEqualTo(gameSessionId);
        verify(gameSessionService).existsForChild(gameSessionId, 15L);
    }

    @Test
    void 최대_질문_수_범위를_벗어나면_저장하지_않는다() {
        assertError(dailyRequest(4), ConversationErrorCode.INVALID_MAX_QUESTION_COUNT);
        assertError(dailyRequest(7), ConversationErrorCode.INVALID_MAX_QUESTION_COUNT);

        verify(conversationSessionRepository, never()).save(any());
    }

    @Test
    void GAME에_게임_세션_ID가_없거나_DAILY에_있으면_거절한다() {
        CreateConversationRequest missingGame = new CreateConversationRequest(
                ContextType.GAME, null, ConversationGoal.DIARY, 6);
        CreateConversationRequest unexpectedGame = new CreateConversationRequest(
                ContextType.DAILY, UUID.randomUUID(), ConversationGoal.DIARY, 6);

        assertError(missingGame, ConversationErrorCode.INVALID_CONVERSATION_CONTEXT);
        assertError(unexpectedGame, ConversationErrorCode.INVALID_CONVERSATION_CONTEXT);
    }

    @Test
    void 게임_세션이_없거나_다른_아이의_것이면_거절한다() {
        UUID gameSessionId = UUID.randomUUID();
        CreateConversationRequest request = new CreateConversationRequest(
                ContextType.GAME, gameSessionId, ConversationGoal.DIARY, 6);
        when(gameSessionService.existsForChild(gameSessionId, 15L)).thenReturn(false);

        assertError(request, ConversationErrorCode.GAME_SESSION_NOT_FOUND);
    }

    @Test
    void 활성_세션이_있으면_중복_생성을_거절한다() {
        when(conversationSessionRepository.existsByChildIdAndStatusIn(
                eq(15L), org.mockito.ArgumentMatchers.<Collection<ConversationStatus>>any()))
                .thenReturn(true);

        assertError(dailyRequest(6), ConversationErrorCode.ACTIVE_CONVERSATION_EXISTS);
        verify(conversationSessionRepository, never()).save(any());
    }

    @Test
    void 아이_접근_검증이_실패하면_나머지_검증과_저장을_진행하지_않는다() {
        org.mockito.Mockito.doThrow(new ConversationException(ConversationErrorCode.CHILD_ACCESS_DENIED))
                .when(childAccessVerifier).verifyAccess(7L, 15L);

        assertError(dailyRequest(6), ConversationErrorCode.CHILD_ACCESS_DENIED);

        verify(conversationSessionRepository, never())
                .existsByChildIdAndStatusIn(
                        anyLong(), org.mockito.ArgumentMatchers.<Collection<ConversationStatus>>any());
        verify(conversationSessionRepository, never()).save(any());
    }

    @Test
    void 접근_가능한_세션을_전체_필드와_KST_시각으로_조회한다() {
        ConversationSession session = ConversationSession.create(
                "conv_query_001", 15L, null, ContextType.DAILY, ConversationGoal.DIARY, 6);
        session.start();
        session.complete();
        ReflectionTestUtils.setField(session, "questionCount", 3);
        ReflectionTestUtils.setField(session, "currentSessionEpoch", 1);
        ReflectionTestUtils.setField(session, "startedAt", LocalDateTime.of(2026, 7, 22, 1, 0, 10));
        ReflectionTestUtils.setField(session, "completedAt", LocalDateTime.of(2026, 7, 22, 1, 8));
        ReflectionTestUtils.setField(session, "terminationReason", "USER_FINISHED");
        when(conversationSessionRepository.findById("conv_query_001")).thenReturn(Optional.of(session));

        ConversationSessionResponse response = service.getConversation("conv_query_001");

        verify(childAccessVerifier).verifyAccess(7L, 15L);
        assertThat(response.conversationId()).isEqualTo("conv_query_001");
        assertThat(response.status()).isEqualTo(ConversationStatus.COMPLETED);
        assertThat(response.questionCount()).isEqualTo(3);
        assertThat(response.maxQuestionCount()).isEqualTo(6);
        assertThat(response.currentSessionEpoch()).isEqualTo(1);
        assertThat(response.startedAt()).isEqualTo(OffsetDateTime.of(
                2026, 7, 22, 10, 0, 10, 0, ZoneOffset.ofHours(9)));
        assertThat(response.completedAt()).isEqualTo(OffsetDateTime.of(
                2026, 7, 22, 10, 8, 0, 0, ZoneOffset.ofHours(9)));
        assertThat(response.terminationReason()).isEqualTo("USER_FINISHED");
    }

    @Test
    void READY_세션의_생명주기_필드는_null로_조회한다() {
        ConversationSession session = ConversationSession.create(
                "conv_query_002", 15L, null, ContextType.DAILY, ConversationGoal.DIARY, 6);
        when(conversationSessionRepository.findById("conv_query_002")).thenReturn(Optional.of(session));

        ConversationSessionResponse response = service.getConversation("conv_query_002");

        assertThat(response.startedAt()).isNull();
        assertThat(response.completedAt()).isNull();
        assertThat(response.terminationReason()).isNull();
    }

    @Test
    void 세션이_없으면_권한을_조회하지_않고_CONVERSATION_NOT_FOUND를_반환한다() {
        when(conversationSessionRepository.findById("conv_missing")).thenReturn(Optional.empty());

        assertGetError("conv_missing", ConversationErrorCode.CONVERSATION_NOT_FOUND);

        verify(currentMemberProvider, never()).getCurrentMemberId();
        verify(childAccessVerifier, never()).verifyAccess(anyLong(), anyLong());
    }

    @Test
    void 다른_보호자의_세션이면_CONVERSATION_ACCESS_DENIED로_변환한다() {
        ConversationSession session = ConversationSession.create(
                "conv_denied", 15L, null, ContextType.DAILY, ConversationGoal.DIARY, 6);
        when(conversationSessionRepository.findById("conv_denied")).thenReturn(Optional.of(session));
        doThrow(new ConversationException(ConversationErrorCode.CHILD_ACCESS_DENIED))
                .when(childAccessVerifier).verifyAccess(7L, 15L);

        assertGetError("conv_denied", ConversationErrorCode.CONVERSATION_ACCESS_DENIED);
    }

    @Test
    void 연결된_아이를_찾을_수_없으면_CONVERSATION_NOT_FOUND로_변환한다() {
        ConversationSession session = ConversationSession.create(
                "conv_orphan", 15L, null, ContextType.DAILY, ConversationGoal.DIARY, 6);
        when(conversationSessionRepository.findById("conv_orphan")).thenReturn(Optional.of(session));
        doThrow(new ConversationException(ConversationErrorCode.CHILD_NOT_FOUND))
                .when(childAccessVerifier).verifyAccess(7L, 15L);

        assertGetError("conv_orphan", ConversationErrorCode.CONVERSATION_NOT_FOUND);
    }

    private CreateConversationRequest dailyRequest(Integer maxQuestionCount) {
        return new CreateConversationRequest(
                ContextType.DAILY, null, ConversationGoal.DIARY, maxQuestionCount);
    }

    private void assertError(CreateConversationRequest request, ConversationErrorCode expected) {
        assertThatThrownBy(() -> service.create(15L, request))
                .isInstanceOfSatisfying(ConversationException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }

    private void assertGetError(String conversationId, ConversationErrorCode expected) {
        assertThatThrownBy(() -> service.getConversation(conversationId))
                .isInstanceOfSatisfying(ConversationException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
