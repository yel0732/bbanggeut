package com.A501.backend.domain.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
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
import com.A501.backend.domain.conversation.exception.ConversationErrorCode;
import com.A501.backend.domain.conversation.exception.ConversationException;
import com.A501.backend.domain.conversation.repository.ConversationTopicStateRepository;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ContextSnapshotBuilderTest {

    @Mock
    private ConversationTopicStateRepository topicStateRepository;

    @Mock
    private RealtimeChildProfileProvider childProfileProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ContextSnapshotBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new ContextSnapshotBuilder(topicStateRepository, childProfileProvider, objectMapper);
    }

    @Test
    void GAME과_DAILY_주제를_topics_배열로_조립한다() {
        ConversationSession session = ConversationSession.create(
                "conv_snapshot_test", 15L, ConversationTopic.GAME, 12);
        RealtimeChildContext childContext = new RealtimeChildContext("하준", "6_9", List.of("공룡"));
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
    void 게임이_없으면_DAILY_단일_주제로_조립한다() {
        ConversationSession session = ConversationSession.create(
                "conv_daily_only", 15L, ConversationTopic.DAILY, 6);
        when(childProfileProvider.getProfile(15L))
                .thenReturn(new RealtimeChildContext("하준", "6_9", List.of()));
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
        when(childProfileProvider.getProfile(15L))
                .thenReturn(new RealtimeChildContext("하준", "6_9", List.of()));
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

    private String serialize(Object value) {
        return objectMapper.writeValueAsString(value);
    }
}
