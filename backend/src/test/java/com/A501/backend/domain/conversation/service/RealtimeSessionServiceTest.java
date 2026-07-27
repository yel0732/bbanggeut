package com.A501.backend.domain.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.A501.backend.domain.conversation.dto.realtime.ContextSnapshot;
import com.A501.backend.domain.conversation.dto.realtime.RealtimeConversationContext;
import com.A501.backend.domain.conversation.dto.realtime.RealtimePolicySnapshot;
import com.A501.backend.domain.conversation.dto.realtime.RealtimeSessionLeaseRecord;
import com.A501.backend.domain.conversation.dto.realtime.RealtimeTicketRecord;
import com.A501.backend.domain.conversation.dto.response.RealtimeSessionResponse;
import com.A501.backend.domain.conversation.entity.ConversationGoal;
import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.ConversationTopic;
import com.A501.backend.domain.conversation.entity.TerminationReason;
import com.A501.backend.domain.conversation.exception.ConversationErrorCode;
import com.A501.backend.domain.conversation.exception.ConversationException;
import com.A501.backend.domain.conversation.repository.ConversationSessionRepository;

@ExtendWith(MockitoExtension.class)
class RealtimeSessionServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-27T01:00:00Z"), ZoneOffset.UTC);

    @Mock
    private ConversationSessionRepository conversationSessionRepository;

    @Mock
    private ChildAccessVerifier childAccessVerifier;

    @Mock
    private CurrentMemberProvider currentMemberProvider;

    @Mock
    private ContextSnapshotBuilder contextSnapshotBuilder;

    @Mock
    private RealtimeContextStore realtimeContextStore;

    @Mock
    private RealtimeTicketProvider realtimeTicketProvider;

    @Mock
    private RealtimeTicketStore realtimeTicketStore;

    @Mock
    private RealtimeSessionLeaseStore realtimeSessionLeaseStore;

    private final RealtimeContextProperties contextProperties =
            new RealtimeContextProperties(Duration.ofMinutes(30));
    private final RealtimeTicketProperties ticketProperties = new RealtimeTicketProperties(
            "spring-backend", "ai-realtime", Duration.ofMinutes(5), null, null);
    private final RealtimeSessionProperties sessionProperties = new RealtimeSessionProperties(
            "ws://localhost:8000/realtime/signaling", List.of(), Duration.ofSeconds(15));

    private RealtimeSessionService service;

    @BeforeEach
    void setUp() {
        service = new RealtimeSessionService(
                conversationSessionRepository,
                childAccessVerifier,
                currentMemberProvider,
                contextSnapshotBuilder,
                realtimeContextStore,
                contextProperties,
                realtimeTicketProvider,
                realtimeTicketStore,
                ticketProperties,
                realtimeSessionLeaseStore,
                sessionProperties,
                FIXED_CLOCK);
    }

    @Test
    void 성공하면_Context_Ticket_Lease를_모두_저장하고_응답을_반환한다() {
        ConversationSession session = ConversationSession.create(
                "conv_1", 15L, ConversationTopic.DAILY, 6);
        when(conversationSessionRepository.findById("conv_1")).thenReturn(Optional.of(session));
        ContextSnapshot snapshot = validSnapshot();
        when(contextSnapshotBuilder.build(session)).thenReturn(snapshot);
        RealtimeTicketIssueResult ticketResult = new RealtimeTicketIssueResult(
                "signed.ticket.jwt", "ticket_1", LocalDateTime.of(2026, 7, 27, 1, 5));
        when(realtimeTicketProvider.issue(eq("conv_1"), any(), eq(1))).thenReturn(ticketResult);

        RealtimeSessionResponse response = service.issue("conv_1");

        assertThat(response.conversationId()).isEqualTo("conv_1");
        assertThat(response.realtimeSessionId()).startsWith("rt_");
        assertThat(response.sessionEpoch()).isEqualTo(1);
        assertThat(response.signalingUrl()).isEqualTo("ws://localhost:8000/realtime/signaling");
        assertThat(response.realtimeTicket()).isEqualTo("signed.ticket.jwt");
        assertThat(response.expiresAt()).isEqualTo(OffsetDateTime.parse("2026-07-27T10:05:00+09:00"));
        assertThat(response.iceServers()).isEmpty();
        assertThat(session.getCurrentSessionEpoch()).isEqualTo(1);

        verify(realtimeContextStore).save(eq("conv_1"), eq(snapshot), eq(Duration.ofMinutes(30)));
        verify(realtimeTicketStore).saveIssued(
                eq("ticket_1"),
                argThatTicketRecordMatches("conv_1", 1, RealtimeTicketRecord.STATE_ISSUED),
                eq(Duration.ofMinutes(5)));
        verify(realtimeSessionLeaseStore).initialize(
                eq("conv_1"),
                argThatLeaseRecordMatches(1, RealtimeSessionLeaseRecord.STATE_ACTIVE),
                eq(Duration.ofSeconds(15)));
    }

    @Test
    void 세션이_없으면_CONVERSATION_NOT_FOUND를_던진다() {
        when(conversationSessionRepository.findById("conv_missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.issue("conv_missing"))
                .isInstanceOf(ConversationException.class)
                .extracting(exception -> ((ConversationException) exception).getErrorCode())
                .isEqualTo(ConversationErrorCode.CONVERSATION_NOT_FOUND);
    }

    @Test
    void 접근_권한이_없으면_CONVERSATION_ACCESS_DENIED를_던진다() {
        ConversationSession session = ConversationSession.create(
                "conv_denied", 15L, ConversationTopic.DAILY, 6);
        when(conversationSessionRepository.findById("conv_denied")).thenReturn(Optional.of(session));
        doThrow(new ConversationException(ConversationErrorCode.CHILD_ACCESS_DENIED))
                .when(childAccessVerifier)
                .verifyAccess(any(), eq(15L));

        assertThatThrownBy(() -> service.issue("conv_denied"))
                .isInstanceOf(ConversationException.class)
                .extracting(exception -> ((ConversationException) exception).getErrorCode())
                .isEqualTo(ConversationErrorCode.CONVERSATION_ACCESS_DENIED);
    }

    @Test
    void 종료된_세션이면_INVALID_SESSION_STATE를_던진다() {
        ConversationSession session = ConversationSession.create(
                "conv_terminal", 15L, ConversationTopic.DAILY, 6);
        session.start();
        session.terminate(LocalDateTime.of(2026, 7, 27, 1, 0), TerminationReason.USER_FINISHED);
        when(conversationSessionRepository.findById("conv_terminal")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.issue("conv_terminal"))
                .isInstanceOf(ConversationException.class)
                .extracting(exception -> ((ConversationException) exception).getErrorCode())
                .isEqualTo(ConversationErrorCode.INVALID_SESSION_STATE);
    }

    @Test
    void Context_저장이_실패하면_REDIS_UNAVAILABLE가_그대로_전파된다() {
        ConversationSession session = ConversationSession.create(
                "conv_redis_fail", 15L, ConversationTopic.DAILY, 6);
        when(conversationSessionRepository.findById("conv_redis_fail")).thenReturn(Optional.of(session));
        ContextSnapshot snapshot = validSnapshot();
        when(contextSnapshotBuilder.build(session)).thenReturn(snapshot);
        doThrow(new ConversationException(ConversationErrorCode.REDIS_UNAVAILABLE))
                .when(realtimeContextStore)
                .save(eq("conv_redis_fail"), eq(snapshot), any());

        assertThatThrownBy(() -> service.issue("conv_redis_fail"))
                .isInstanceOf(ConversationException.class)
                .extracting(exception -> ((ConversationException) exception).getErrorCode())
                .isEqualTo(ConversationErrorCode.REDIS_UNAVAILABLE);
    }

    private ContextSnapshot validSnapshot() {
        return ContextSnapshot.create(
                "conv_1",
                new com.A501.backend.domain.conversation.dto.realtime.RealtimeChildContext("하준"),
                new RealtimeConversationContext(ConversationGoal.DIARY, "ko-KR", ConversationTopic.DAILY, 6),
                null,
                List.of(),
                RealtimePolicySnapshot.defaultPolicy());
    }

    private RealtimeTicketRecord argThatTicketRecordMatches(
            String conversationId, int sessionEpoch, String state) {
        return org.mockito.ArgumentMatchers.argThat(record ->
                record != null
                        && record.conversationId().equals(conversationId)
                        && record.sessionEpoch() == sessionEpoch
                        && record.state().equals(state));
    }

    private RealtimeSessionLeaseRecord argThatLeaseRecordMatches(int sessionEpoch, String state) {
        return org.mockito.ArgumentMatchers.argThat(record ->
                record != null
                        && record.sessionEpoch() == sessionEpoch
                        && record.state().equals(state));
    }
}
