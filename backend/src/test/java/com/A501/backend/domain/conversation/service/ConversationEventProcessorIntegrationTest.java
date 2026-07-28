package com.A501.backend.domain.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.A501.backend.domain.conversation.dto.stream.ConversationStreamDelivery;
import com.A501.backend.domain.conversation.entity.ConversationEventProcessingResult;
import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.ConversationStatus;
import com.A501.backend.domain.conversation.entity.ConversationTopic;
import com.A501.backend.domain.conversation.entity.ConversationTopicState;
import com.A501.backend.domain.conversation.entity.ConversationTopicStatus;
import com.A501.backend.domain.conversation.entity.ConversationTurn;
import com.A501.backend.domain.conversation.entity.Speaker;
import com.A501.backend.domain.conversation.entity.TurnStatus;
import com.A501.backend.domain.conversation.entity.TerminationReason;
import com.A501.backend.domain.conversation.repository.ConversationEventFailureRepository;
import com.A501.backend.domain.conversation.repository.ConversationEventRepository;
import com.A501.backend.domain.conversation.repository.ConversationSessionRepository;
import com.A501.backend.domain.conversation.repository.ConversationTopicStateRepository;
import com.A501.backend.domain.conversation.repository.ConversationTurnRepository;

@SpringBootTest
class ConversationEventProcessorIntegrationTest {

    private static final String CONVERSATION_ID = "conv_stream_test";

    @Autowired
    private ConversationEventProcessor eventProcessor;

    @Autowired
    private ConversationSessionRepository sessionRepository;

    @Autowired
    private ConversationTopicStateRepository topicStateRepository;

    @Autowired
    private ConversationTurnRepository turnRepository;

    @Autowired
    private ConversationEventRepository eventRepository;

    @Autowired
    private ConversationEventFailureRepository failureRepository;

    @BeforeEach
    void setUp() {
        failureRepository.deleteAll();
        eventRepository.deleteAll();
        turnRepository.deleteAll();
        topicStateRepository.deleteAll();
        sessionRepository.deleteAll();
        saveConversation(1);
    }

    @Test
    void persistsChildTurnAndTreatsDuplicateEventIdAsSuccess() {
        ConversationStreamDelivery delivery = delivery(
                "1000-0",
                "evt-child-1",
                "CHILD_TURN_FINALIZED",
                1,
                "turn-child-1",
                1,
                null,
                """
                {
                  "text": "오늘 공원에서 뛰어놀았어",
                  "sttConfidence": 0.93,
                  "startedAt": "2026-07-28T01:00:00Z",
                  "completedAt": "2026-07-28T01:00:03Z"
                }
                """);

        assertThat(eventProcessor.process(delivery))
                .isEqualTo(ConversationEventProcessingOutcome.APPLIED);
        assertThat(eventProcessor.process(new ConversationStreamDelivery(
                        "1000-1",
                        delivery.eventJson(),
                        1)))
                .isEqualTo(ConversationEventProcessingOutcome.DUPLICATE);

        ConversationTurn turn = turnRepository.findById("turn-child-1").orElseThrow();
        assertThat(turn.getSpeaker()).isEqualTo(Speaker.CHILD);
        assertThat(turn.getStatus()).isEqualTo(TurnStatus.COMPLETED);
        assertThat(turn.getContent()).isEqualTo("오늘 공원에서 뛰어놀았어");
        assertThat(turn.getTopicType()).isEqualTo(ConversationTopic.GAME);
        assertThat(turnRepository.count()).isEqualTo(1);
        assertThat(eventRepository.count()).isEqualTo(1);
    }

    @Test
    void appliesAiGeneratedThenCompletedAndSynchronizesTopicSlots() {
        ConversationStreamDelivery generated = delivery(
                "2000-0",
                "evt-ai-generated-1",
                "AI_TURN_GENERATED",
                1,
                "turn-ai-1",
                2,
                "resp-ai-1",
                """
                {
                  "text": "그때 어떤 기분이 들었어?",
                  "questionText": "그때 어떤 기분이 들었어?",
                  "targetSlot": "emotion",
                  "topicType": "GAME",
                  "slotsTopicType": "GAME",
                  "topicSlots": {
                    "event": "공원에서 달리기",
                    "emotion": null
                  },
                  "topicQuestionCount": 1,
                  "sessionQuestionCount": 1,
                  "topicCompletionReason": null
                }
                """);
        ConversationStreamDelivery completed = delivery(
                "2000-1",
                "evt-ai-completed-1",
                "AI_TURN_COMPLETED",
                1,
                "turn-ai-1",
                2,
                "resp-ai-1",
                """
                {
                  "text": "그때 어떤 기분이 들었어?",
                  "audioStartedAt": "2026-07-28T01:00:05Z",
                  "audioCompletedAt": "2026-07-28T01:00:08Z"
                }
                """);

        assertThat(eventProcessor.process(generated))
                .isEqualTo(ConversationEventProcessingOutcome.APPLIED);
        assertThat(eventProcessor.process(completed))
                .isEqualTo(ConversationEventProcessingOutcome.APPLIED);

        ConversationTurn turn = turnRepository.findById("turn-ai-1").orElseThrow();
        assertThat(turn.getSpeaker()).isEqualTo(Speaker.AI);
        assertThat(turn.getStatus()).isEqualTo(TurnStatus.COMPLETED);
        assertThat(turn.getResponseId()).isEqualTo("resp-ai-1");
        assertThat(turn.getCompletedAt()).isEqualTo("2026-07-28T01:00:08");
        assertThat(turn.getVersion()).isEqualTo(1L);
        ConversationTopicState topic = topicStateRepository
                .findByConversationIdAndTopicType(CONVERSATION_ID, ConversationTopic.GAME)
                .orElseThrow();
        assertThat(topic.getSlotsJson()).contains("\"event\":\"공원에서 달리기\"");
        assertThat(topic.getQuestionCount()).isEqualTo(1);
        assertThat(topic.getStatus()).isEqualTo(ConversationTopicStatus.IN_PROGRESS);
        assertThat(sessionRepository.findById(CONVERSATION_ID).orElseThrow().getQuestionCount())
                .isEqualTo(1);
    }

    @Test
    void recordsAndAcknowledgesConceptuallyStaleEpochWithoutApplyingPayload() {
        ConversationSession session = sessionRepository.findById(CONVERSATION_ID).orElseThrow();
        session.startRealtimeSession(LocalDateTime.parse("2026-07-28T01:00:03"));
        sessionRepository.saveAndFlush(session);
        ConversationStreamDelivery stale = delivery(
                "3000-0",
                "evt-stale-1",
                "CHILD_TURN_FINALIZED",
                1,
                "turn-stale-1",
                1,
                null,
                """
                {
                  "text": "오래된 연결의 말",
                  "sttConfidence": 0.88,
                  "startedAt": "2026-07-28T01:00:00Z",
                  "completedAt": "2026-07-28T01:00:03Z"
                }
                """);

        assertThat(eventProcessor.process(stale))
                .isEqualTo(ConversationEventProcessingOutcome.IGNORED_STALE);

        assertThat(turnRepository.findById("turn-stale-1")).isEmpty();
        assertThat(eventRepository.findById("evt-stale-1"))
                .get()
                .extracting(event -> event.getProcessingResult())
                .isEqualTo(ConversationEventProcessingResult.IGNORED_STALE);
    }

    @Test
    void ignoresLateTurnEventForTerminalSessionSoPurgedContentIsNotRecreated() {
        // 이탈·중단으로 원문을 정리한 뒤 늦게 도착한 확정 턴 이벤트가 턴을 재생성하면 안 된다.
        ConversationSession session = sessionRepository.findById(CONVERSATION_ID).orElseThrow();
        session.terminate(
                LocalDateTime.parse("2026-07-28T01:00:10"),
                TerminationReason.CONNECTION_LOST);
        sessionRepository.saveAndFlush(session);
        ConversationStreamDelivery late = delivery(
                "6000-0",
                "evt-late-after-abort-1",
                "CHILD_TURN_FINALIZED",
                1,
                "turn-late-after-abort-1",
                1,
                null,
                """
                {
                  "text": "정리 후 늦게 도착한 말",
                  "sttConfidence": 0.9,
                  "startedAt": "2026-07-28T01:00:00Z",
                  "completedAt": "2026-07-28T01:00:03Z"
                }
                """);

        assertThat(eventProcessor.process(late))
                .isEqualTo(ConversationEventProcessingOutcome.IGNORED_STALE);
        assertThat(turnRepository.findById("turn-late-after-abort-1")).isEmpty();
        assertThat(eventRepository.findById("evt-late-after-abort-1"))
                .get()
                .extracting(event -> event.getProcessingResult())
                .isEqualTo(ConversationEventProcessingResult.IGNORED_STALE);
    }

    @Test
    void completesAiTurnEvenWhenCompletedTextDiffersFromGeneratedText() {
        // finding ③: COMPLETED의 정체성은 식별자로 판정한다 — text가 GENERATED와
        // 달라도(향후 TTS가 다른 텍스트를 실을 수 있음) 턴은 정상 완료돼야 한다.
        ConversationStreamDelivery generated = delivery(
                "2100-0",
                "evt-ai-generated-diff",
                "AI_TURN_GENERATED",
                1,
                "turn-ai-diff",
                2,
                "resp-ai-diff",
                """
                {
                  "text": "그때 어떤 기분이 들었어?",
                  "questionText": "그때 어떤 기분이 들었어?",
                  "targetSlot": "emotion",
                  "topicType": "GAME",
                  "slotsTopicType": "GAME",
                  "topicSlots": { "event": "공원에서 달리기", "emotion": null },
                  "topicQuestionCount": 1,
                  "sessionQuestionCount": 1,
                  "topicCompletionReason": null
                }
                """);
        ConversationStreamDelivery completed = delivery(
                "2100-1",
                "evt-ai-completed-diff",
                "AI_TURN_COMPLETED",
                1,
                "turn-ai-diff",
                2,
                "resp-ai-diff",
                """
                {
                  "text": "그때 어떤 기분이 들었어? (음성으로 다시 표현)",
                  "audioStartedAt": "2026-07-28T01:00:05Z",
                  "audioCompletedAt": "2026-07-28T01:00:08Z"
                }
                """);

        assertThat(eventProcessor.process(generated))
                .isEqualTo(ConversationEventProcessingOutcome.APPLIED);
        assertThat(eventProcessor.process(completed))
                .isEqualTo(ConversationEventProcessingOutcome.APPLIED);

        ConversationTurn turn = turnRepository.findById("turn-ai-diff").orElseThrow();
        assertThat(turn.getStatus()).isEqualTo(TurnStatus.COMPLETED);
        // content는 GENERATED가 저장한 값 그대로 유지된다.
        assertThat(turn.getContent()).isEqualTo("그때 어떤 기분이 들었어?");
    }

    @Test
    void appliesConfirmedPriorEpochTurnThatOccurredBeforeReconnectCutover() {
        ConversationSession session = sessionRepository.findById(CONVERSATION_ID).orElseThrow();
        session.startRealtimeSession(LocalDateTime.parse("2026-07-28T01:00:05"));
        sessionRepository.saveAndFlush(session);
        ConversationStreamDelivery confirmedBeforeCutover = delivery(
                "3100-0",
                "evt-prior-epoch-confirmed-1",
                "CHILD_TURN_FINALIZED",
                1,
                "turn-prior-epoch-1",
                1,
                null,
                """
                {
                  "text": "재연결 전에 확정된 답변",
                  "sttConfidence": 0.91,
                  "startedAt": "2026-07-28T01:00:00Z",
                  "completedAt": "2026-07-28T01:00:03Z",
                  "topicType": "GAME"
                }
                """);

        assertThat(eventProcessor.process(confirmedBeforeCutover))
                .isEqualTo(ConversationEventProcessingOutcome.APPLIED);
        assertThat(turnRepository.findById("turn-prior-epoch-1")).isPresent();
        assertThat(eventRepository.findById("evt-prior-epoch-confirmed-1"))
                .get()
                .extracting(event -> event.getProcessingResult())
                .isEqualTo(ConversationEventProcessingResult.APPLIED);
    }

    @Test
    void switchesTopicAndSynchronizesProgressFromTransitionQuestion() {
        eventProcessor.process(delivery(
                "3200-0",
                "evt-session-started-switch",
                "SESSION_STARTED",
                1,
                null,
                null,
                null,
                """
                { "connectedAt": "2026-07-28T01:00:00Z" }
                """));
        ConversationStreamDelivery afterSwitch = delivery(
                "3200-2",
                "evt-ai-after-switch-1",
                "AI_TURN_GENERATED",
                1,
                "turn-ai-after-switch-1",
                4,
                "resp-ai-after-switch-1",
                """
                {
                  "text": "산책했구나! 어디에서 산책했어?",
                  "questionText": "어디에서 산책했어?",
                  "targetSlot": "place",
                  "topicType": "DAILY",
                  "slotsTopicType": "DAILY",
                  "topicSlots": {
                    "event": "산책",
                    "place": null
                  },
                  "topicQuestionCount": 2,
                  "sessionQuestionCount": 2,
                  "topicCompletionReason": null
                }
                """);
        assertThatThrownBy(() -> eventProcessor.process(afterSwitch))
                .isInstanceOf(ConversationEventContractException.class)
                .hasMessageContaining("transition");
        assertThat(turnRepository.findById("turn-ai-after-switch-1")).isEmpty();
        assertThat(eventRepository.findById("evt-ai-after-switch-1")).isEmpty();

        ConversationStreamDelivery switched = delivery(
                "3200-1",
                "evt-ai-switch-1",
                "AI_TURN_GENERATED",
                1,
                "turn-ai-switch-1",
                2,
                "resp-ai-switch-1",
                """
                {
                  "text": "게임 이야기 고마워! 오늘 다른 일도 이야기해 줄래?",
                  "questionText": "오늘 다른 일도 이야기해 줄래?",
                  "targetSlot": null,
                  "topicType": "DAILY",
                  "slotsTopicType": "GAME",
                  "topicSlots": {
                    "event": "과일 게임",
                    "emotion": "즐거움"
                  },
                  "topicQuestionCount": 1,
                  "sessionQuestionCount": 1,
                  "topicCompletionReason": "REQUIRED_SLOTS_FILLED"
                }
                """);

        assertThat(eventProcessor.process(switched))
                .isEqualTo(ConversationEventProcessingOutcome.APPLIED);

        ConversationSession session =
                sessionRepository.findById(CONVERSATION_ID).orElseThrow();
        ConversationTopicState game = topicStateRepository
                .findByConversationIdAndTopicType(CONVERSATION_ID, ConversationTopic.GAME)
                .orElseThrow();
        ConversationTopicState daily = topicStateRepository
                .findByConversationIdAndTopicType(CONVERSATION_ID, ConversationTopic.DAILY)
                .orElseThrow();
        ConversationTurn turn =
                turnRepository.findById("turn-ai-switch-1").orElseThrow();

        assertThat(session.getCurrentTopic()).isEqualTo(ConversationTopic.DAILY);
        assertThat(session.getQuestionCount()).isEqualTo(1);
        assertThat(game.getStatus()).isEqualTo(ConversationTopicStatus.COMPLETED);
        assertThat(game.getCompletionReason()).isEqualTo(TerminationReason.REQUIRED_SLOTS_FILLED);
        assertThat(game.getSlotsJson()).contains("\"event\":\"과일 게임\"");
        assertThat(daily.getStatus()).isEqualTo(ConversationTopicStatus.IN_PROGRESS);
        assertThat(daily.getQuestionCount()).isEqualTo(1);
        assertThat(turn.getTopicType()).isEqualTo(ConversationTopic.DAILY);
        assertThat(turn.getQuestionText()).isEqualTo("오늘 다른 일도 이야기해 줄래?");

        assertThat(eventProcessor.process(afterSwitch))
                .isEqualTo(ConversationEventProcessingOutcome.APPLIED);
        ConversationSession progressed =
                sessionRepository.findById(CONVERSATION_ID).orElseThrow();
        ConversationTopicState progressedDaily = topicStateRepository
                .findByConversationIdAndTopicType(CONVERSATION_ID, ConversationTopic.DAILY)
                .orElseThrow();
        assertThat(progressed.getQuestionCount()).isEqualTo(2);
        assertThat(progressedDaily.getQuestionCount()).isEqualTo(2);
        assertThat(progressedDaily.getSlotsJson()).contains("\"event\":\"산책\"");
    }

    @Test
    void rejectsFutureEpochAndLeavesNoProcessingReceipt() {
        ConversationStreamDelivery future = delivery(
                "4000-0",
                "evt-future-1",
                "SESSION_STARTED",
                2,
                null,
                null,
                null,
                """
                {
                  "connectedAt": "2026-07-28T01:00:00Z"
                }
                """);

        assertThatThrownBy(() -> eventProcessor.process(future))
                .isInstanceOf(ConversationEventContractException.class)
                .hasMessageContaining("ahead");
        assertThat(eventRepository.findById("evt-future-1")).isEmpty();
    }

    @Test
    void appliesSessionStartedAndCompletedEvents() {
        ConversationStreamDelivery started = delivery(
                "5000-0",
                "evt-session-started-1",
                "SESSION_STARTED",
                1,
                null,
                null,
                null,
                """
                {
                  "connectedAt": "2026-07-28T01:00:00Z"
                }
                """);
        ConversationStreamDelivery completed = delivery(
                "5000-1",
                "evt-session-completed-1",
                "SESSION_COMPLETED",
                1,
                null,
                null,
                null,
                """
                {
                  "reason": "USER_FINISHED",
                  "completedAt": "2026-07-28T01:05:00Z"
                }
                """);

        assertThat(eventProcessor.process(started))
                .isEqualTo(ConversationEventProcessingOutcome.APPLIED);
        assertThat(eventProcessor.process(completed))
                .isEqualTo(ConversationEventProcessingOutcome.APPLIED);

        ConversationSession session =
                sessionRepository.findById(CONVERSATION_ID).orElseThrow();
        assertThat(session.getStatus()).isEqualTo(ConversationStatus.COMPLETED);
        assertThat(session.getStartedAt()).isEqualTo("2026-07-28T01:00:00");
        assertThat(session.getCompletedAt()).isEqualTo("2026-07-28T01:05:00");
        assertThat(topicStateRepository
                        .findByConversationIdAndTopicType(
                                CONVERSATION_ID,
                                ConversationTopic.GAME)
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(ConversationTopicStatus.COMPLETED);
    }

    private void saveConversation(int epochCount) {
        ConversationSession session = ConversationSession.create(
                CONVERSATION_ID,
                15L,
                ConversationTopic.GAME,
                12);
        for (int count = 0; count < epochCount; count++) {
            session.startRealtimeSession();
        }
        sessionRepository.saveAndFlush(session);
        topicStateRepository.saveAndFlush(ConversationTopicState.create(
                "topic-game-stream-test",
                CONVERSATION_ID,
                ConversationTopic.GAME,
                1,
                6,
                "{}",
                "{}"));
        topicStateRepository.saveAndFlush(ConversationTopicState.create(
                "topic-daily-stream-test",
                CONVERSATION_ID,
                ConversationTopic.DAILY,
                2,
                6,
                "{}",
                "{}"));
    }

    private static ConversationStreamDelivery delivery(
            String recordId,
            String eventId,
            String eventType,
            int sessionEpoch,
            String turnId,
            Integer turnSeq,
            String responseId,
            String payload) {
        String json = """
                {
                  "eventId": "%s",
                  "eventType": "%s",
                  "conversationId": "%s",
                  "realtimeSessionId": "rt-stream-test",
                  "sessionEpoch": %d,
                  "turnId": %s,
                  "turnSeq": %s,
                  "responseId": %s,
                  "occurredAt": "2026-07-28T01:00:04Z",
                  "payload": %s
                }
                """.formatted(
                eventId,
                eventType,
                CONVERSATION_ID,
                sessionEpoch,
                jsonString(turnId),
                turnSeq == null ? "null" : turnSeq,
                jsonString(responseId),
                payload);
        return new ConversationStreamDelivery(recordId, json, 1);
    }

    private static String jsonString(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
