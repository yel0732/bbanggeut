package com.A501.backend.domain.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.A501.backend.domain.conversation.dto.internal.DialogueGameContextPayload;
import com.A501.backend.domain.conversation.dto.internal.DialogueSlotsPayload;
import com.A501.backend.domain.conversation.dto.internal.DialogueTopicContextPayload;
import com.A501.backend.domain.conversation.dto.realtime.ContextSnapshot;
import com.A501.backend.domain.conversation.dto.realtime.RealtimeChildContext;
import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.ConversationTopic;
import com.A501.backend.domain.conversation.entity.ConversationTopicState;
import com.A501.backend.domain.conversation.entity.ConversationTurn;
import com.A501.backend.domain.conversation.entity.Speaker;
import com.A501.backend.domain.conversation.entity.TurnStatus;
import com.A501.backend.domain.conversation.exception.ConversationErrorCode;
import com.A501.backend.domain.conversation.exception.ConversationException;
import com.A501.backend.domain.conversation.repository.ConversationSessionRepository;
import com.A501.backend.domain.conversation.repository.ConversationTopicStateRepository;
import com.A501.backend.domain.conversation.repository.ConversationTurnRepository;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ContextSnapshotBuilderTest {

    @Mock
    private ConversationSessionRepository conversationSessionRepository;

    @Mock
    private ConversationTopicStateRepository topicStateRepository;

    @Mock
    private ConversationTurnRepository conversationTurnRepository;

    @Mock
    private RealtimeChildProfileProvider childProfileProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ContextSnapshotBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new ContextSnapshotBuilder(
                conversationSessionRepository,
                topicStateRepository,
                conversationTurnRepository,
                childProfileProvider,
                objectMapper);
    }

    @Test
    void GAME과_DAILY_주제를_topics_배열로_조립한다() {
        ConversationSession session = ConversationSession.create(
                "conv_snapshot_test", 15L, ConversationTopic.GAME, 12);
        RealtimeChildContext childContext = new RealtimeChildContext("하준");
        when(childProfileProvider.getProfile(15L)).thenReturn(childContext);
        when(topicStateRepository.findAllByConversationIdOrderByTopicOrderAsc("conv_snapshot_test"))
                .thenReturn(List.of(
                        gameTopic(),
                        dailyTopic()));

        ContextSnapshot snapshot = builder.build(session);

        assertThat(snapshot.conversationId()).isEqualTo("conv_snapshot_test");
        assertThat(snapshot.child()).isEqualTo(childContext);
        assertThat(snapshot.conversation().currentTopic()).isEqualTo(ConversationTopic.GAME);
        assertThat(snapshot.conversation().totalMaxQuestionCount()).isEqualTo(12);
        assertThat(snapshot.topics()).hasSize(2);
        assertThat(snapshot.topics().get(0).type()).isEqualTo(ConversationTopic.GAME);
        assertThat(snapshot.topics().get(0).games()).hasSize(1);
        assertThat(snapshot.topics().get(0).games().get(0).score()).isEqualTo(820);
        assertThat(snapshot.topics().get(1).type()).isEqualTo(ConversationTopic.DAILY);
        assertThat(snapshot.topics().get(1).games()).isEmpty();
    }

    @Test
    void 저장된_마지막_turnSeq를_lastTurnSeq로_담는다() {
        ConversationSession session = ConversationSession.create(
                "conv_reconnect", 15L, ConversationTopic.DAILY, 6);
        when(childProfileProvider.getProfile(15L)).thenReturn(new RealtimeChildContext("하준"));
        when(topicStateRepository.findAllByConversationIdOrderByTopicOrderAsc("conv_reconnect"))
                .thenReturn(List.of(dailyTopic()));
        when(conversationTurnRepository.findMaxTurnSeqByConversationId("conv_reconnect"))
                .thenReturn(7);

        ContextSnapshot snapshot = builder.build(session);

        // 재연결 시 FastAPI는 8번부터 turnSeq를 발급해 저장된 턴과 충돌하지 않는다.
        assertThat(snapshot.conversation().lastTurnSeq()).isEqualTo(7);
    }

    @Test
    void 턴이_없으면_lastTurnSeq는_0이다() {
        ConversationSession session = ConversationSession.create(
                "conv_fresh", 15L, ConversationTopic.DAILY, 6);
        when(childProfileProvider.getProfile(15L)).thenReturn(new RealtimeChildContext("하준"));
        when(topicStateRepository.findAllByConversationIdOrderByTopicOrderAsc("conv_fresh"))
                .thenReturn(List.of(dailyTopic()));

        ContextSnapshot snapshot = builder.build(session);

        assertThat(snapshot.conversation().lastTurnSeq()).isZero();
    }

    @Test
    void 게임이_없으면_DAILY_단일_주제로_조립한다() {
        ConversationSession session = ConversationSession.create(
                "conv_daily_only", 15L, ConversationTopic.DAILY, 6);
        when(childProfileProvider.getProfile(15L)).thenReturn(new RealtimeChildContext("하준"));
        when(topicStateRepository.findAllByConversationIdOrderByTopicOrderAsc("conv_daily_only"))
                .thenReturn(List.of(dailyTopic()));

        ContextSnapshot snapshot = builder.build(session);

        assertThat(snapshot.topics()).hasSize(1);
        assertThat(snapshot.conversation().totalMaxQuestionCount()).isEqualTo(6);
    }

    @Test
    void 주제_슬롯이_이미_채워졌으면_현재_값을_그대로_반영한다() {
        ConversationSession session = ConversationSession.create(
                "conv_in_progress", 15L, ConversationTopic.DAILY, 6);
        when(childProfileProvider.getProfile(15L)).thenReturn(new RealtimeChildContext("하준"));
        ConversationTopicState topicInProgress = ConversationTopicState.create(
                "topic_daily_in_progress",
                "conv_in_progress",
                ConversationTopic.DAILY,
                1,
                6,
                serialize(new DialogueSlotsPayload("놀이공원", List.of("엄마"), null, "신남", null, null)),
                serialize(DialogueTopicContextPayload.empty(ConversationTopic.DAILY)));
        when(topicStateRepository.findAllByConversationIdOrderByTopicOrderAsc("conv_in_progress"))
                .thenReturn(List.of(topicInProgress));

        ContextSnapshot snapshot = builder.build(session);

        assertThat(snapshot.topics().get(0).initialSlots().event()).isEqualTo("놀이공원");
        assertThat(snapshot.topics().get(0).initialSlots().people()).containsExactly("엄마");
    }

    @Test
    void 이전에_종료된_대화가_있으면_최근_아이_답변을_참고_정보로_담는다() {
        ConversationSession session = ConversationSession.create(
                "conv_with_history", 15L, ConversationTopic.DAILY, 6);
        when(childProfileProvider.getProfile(15L)).thenReturn(new RealtimeChildContext("하준"));
        when(topicStateRepository.findAllByConversationIdOrderByTopicOrderAsc("conv_with_history"))
                .thenReturn(List.of(dailyTopic()));
        ConversationSession previousSession = ConversationSession.create(
                "conv_previous", 15L, ConversationTopic.DAILY, 6);
        when(conversationSessionRepository.findFirstByChildIdAndStatusInOrderByCreatedAtDesc(
                        eq(15L), any()))
                .thenReturn(Optional.of(previousSession));
        when(conversationTurnRepository.findTop5ByConversationIdAndSpeakerAndStatusOrderByTurnSeqDesc(
                        "conv_previous", Speaker.CHILD, TurnStatus.COMPLETED))
                .thenReturn(List.of(
                        childTurn("conv_previous", 3, "그다음 낮잠도 잤어"),
                        childTurn("conv_previous", 1, "오늘 놀이터에서 놀았어")));

        ContextSnapshot snapshot = builder.build(session);

        assertThat(snapshot.recentConversationSummary())
                .isEqualTo("오늘 놀이터에서 놀았어 그다음 낮잠도 잤어");
    }

    @Test
    void 이전에_종료된_대화가_없으면_참고_정보는_null이다() {
        ConversationSession session = ConversationSession.create(
                "conv_no_history", 15L, ConversationTopic.DAILY, 6);
        when(childProfileProvider.getProfile(15L)).thenReturn(new RealtimeChildContext("하준"));
        when(topicStateRepository.findAllByConversationIdOrderByTopicOrderAsc("conv_no_history"))
                .thenReturn(List.of(dailyTopic()));
        when(conversationSessionRepository.findFirstByChildIdAndStatusInOrderByCreatedAtDesc(
                        eq(15L), any()))
                .thenReturn(Optional.empty());

        ContextSnapshot snapshot = builder.build(session);

        assertThat(snapshot.recentConversationSummary()).isNull();
    }

    @Test
    void 슬롯_JSON이_손상되면_거부한다() {
        ConversationSession session = ConversationSession.create(
                "conv_broken_slots", 15L, ConversationTopic.DAILY, 6);
        ConversationTopicState brokenTopic = ConversationTopicState.create(
                "topic_broken", "conv_broken_slots", ConversationTopic.DAILY, 1, 6,
                "not-json", serialize(DialogueTopicContextPayload.empty(ConversationTopic.DAILY)));
        when(topicStateRepository.findAllByConversationIdOrderByTopicOrderAsc("conv_broken_slots"))
                .thenReturn(List.of(brokenTopic));

        assertThatThrownBy(() -> builder.build(session))
                .isInstanceOf(ConversationException.class)
                .extracting(exception -> ((ConversationException) exception).getErrorCode())
                .isEqualTo(ConversationErrorCode.DIALOGUE_STATE_INVALID);
    }

    @Test
    void Context_JSON이_손상되면_거부한다() {
        ConversationSession session = ConversationSession.create(
                "conv_broken_context", 15L, ConversationTopic.DAILY, 6);
        ConversationTopicState brokenTopic = ConversationTopicState.create(
                "topic_broken_context", "conv_broken_context", ConversationTopic.DAILY, 1, 6,
                serialize(DialogueSlotsPayload.empty()), "not-json");
        when(topicStateRepository.findAllByConversationIdOrderByTopicOrderAsc("conv_broken_context"))
                .thenReturn(List.of(brokenTopic));

        assertThatThrownBy(() -> builder.build(session))
                .isInstanceOf(ConversationException.class)
                .extracting(exception -> ((ConversationException) exception).getErrorCode())
                .isEqualTo(ConversationErrorCode.DIALOGUE_STATE_INVALID);
    }

    private ConversationTopicState gameTopic() {
        return ConversationTopicState.create(
                "topic_game",
                "conv_snapshot_test",
                ConversationTopic.GAME,
                1,
                6,
                serialize(DialogueSlotsPayload.empty()),
                serialize(new DialogueTopicContextPayload(
                        ConversationTopic.GAME,
                        List.of(new DialogueGameContextPayload(
                                UUID.randomUUID(), 1L, 820, 180,
                                LocalDateTime.of(2026, 7, 27, 10, 0))))));
    }

    private ConversationTopicState dailyTopic() {
        return ConversationTopicState.create(
                "topic_daily",
                "conv_snapshot_test",
                ConversationTopic.DAILY,
                2,
                6,
                serialize(DialogueSlotsPayload.empty()),
                serialize(DialogueTopicContextPayload.empty(ConversationTopic.DAILY)));
    }

    private ConversationTurn childTurn(String conversationId, int turnSeq, String content) {
        return ConversationTurn.createChildCompleted(
                "turn_" + conversationId + "_" + turnSeq,
                conversationId,
                turnSeq,
                content,
                (BigDecimal) null,
                LocalDateTime.of(2026, 7, 20, 9, 0),
                LocalDateTime.of(2026, 7, 20, 9, 1));
    }

    private String serialize(Object value) {
        return objectMapper.writeValueAsString(value);
    }
}
