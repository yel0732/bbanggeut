package com.A501.backend.domain.conversation.dto.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

/**
 * FastAPI Producer가 발행하는 JSON을 Spring이 그대로 역직렬화하는지 검증한다
 * (DIARY-3-W05 완료 조건 1). 아래 JSON 문자열은 ai-server의
 * {@code tests/events/test_stream_schema.py} fixture와 문자 그대로 같아야 한다 —
 * 설계 문서가 로컬 gitignore라 이 두 fixture가 계약의 실질적 진실 원천이다.
 */
class ConversationStreamEventContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void CHILD_TURN_FINALIZED를_역직렬화한다() {
        String json = """
                {
                  "eventId": "evt_internal_002",
                  "eventType": "CHILD_TURN_FINALIZED",
                  "conversationId": "conv_01JZ123ABC",
                  "realtimeSessionId": "rt_01JZ456DEF",
                  "sessionEpoch": 1,
                  "turnId": "turn_002",
                  "turnSeq": 2,
                  "responseId": null,
                  "occurredAt": "2026-07-22T10:00:30+00:00",
                  "payload": {
                    "text": "과일이 많이 나왔을 때 재미있었어.",
                    "sttConfidence": 0.91,
                    "startedAt": "2026-07-22T10:00:20+00:00",
                    "completedAt": "2026-07-22T10:00:30+00:00",
                    "topicType": "GAME"
                  }
                }
                """;

        ConversationStreamEvent event = objectMapper.readValue(json, ConversationStreamEvent.class);

        assertThat(event.eventType()).isEqualTo(ConversationStreamEventType.CHILD_TURN_FINALIZED);
        assertThat(event.conversationId()).isEqualTo("conv_01JZ123ABC");
        assertThat(event.sessionEpoch()).isEqualTo(1);
        assertThat(event.turnId()).isEqualTo("turn_002");
        assertThat(event.turnSeq()).isEqualTo(2);
        assertThat(event.responseId()).isNull();
        assertThat(event.occurredAt()).isEqualTo(OffsetDateTime.parse("2026-07-22T10:00:30+00:00"));
        assertThat(event.payload().get("text").asString())
                .isEqualTo("과일이 많이 나왔을 때 재미있었어.");
        assertThat(event.payload().get("sttConfidence").asDouble()).isEqualTo(0.91);
        assertThat(event.payload().get("topicType").asString()).isEqualTo("GAME");
    }

    @Test
    void AI_TURN_GENERATED는_슬롯_동기화_필드를_담는다() {
        String json = """
                {
                  "eventId": "evt_internal_003",
                  "eventType": "AI_TURN_GENERATED",
                  "conversationId": "conv_01JZ123ABC",
                  "realtimeSessionId": "rt_01JZ456DEF",
                  "sessionEpoch": 1,
                  "turnId": "turn_003",
                  "turnSeq": 3,
                  "responseId": "resp_003",
                  "occurredAt": "2026-07-22T10:00:31+00:00",
                  "payload": {
                    "text": "그때 어떤 기분이 들었어?",
                    "questionText": "그때 어떤 기분이 들었어?",
                    "targetSlot": "emotion",
                    "topicType": "GAME",
                    "slotsTopicType": "GAME",
                    "topicSlots": {
                      "event": "과일 자르기", "people": [], "place": null,
                      "emotion": null, "emotionReason": null, "memorableMoment": null
                    },
                    "skippedTargets": ["memorableMoment"],
                    "topicQuestionCount": 2,
                    "sessionQuestionCount": 2,
                    "topicCompletionReason": null
                  }
                }
                """;

        ConversationStreamEvent event = objectMapper.readValue(json, ConversationStreamEvent.class);

        assertThat(event.eventType()).isEqualTo(ConversationStreamEventType.AI_TURN_GENERATED);
        assertThat(event.responseId()).isEqualTo("resp_003");
        assertThat(event.payload().get("targetSlot").asString()).isEqualTo("emotion");
        // §11-B: 재연결 스냅숏이 최신 슬롯을 담게 하는 동기화 필드
        assertThat(event.payload().get("topicType").asString()).isEqualTo("GAME");
        assertThat(event.payload().get("slotsTopicType").asString()).isEqualTo("GAME");
        assertThat(event.payload().get("topicQuestionCount").asInt()).isEqualTo(2);
        assertThat(event.payload().get("sessionQuestionCount").asInt()).isEqualTo(2);
        assertThat(event.payload().get("skippedTargets").get(0).asString())
                .isEqualTo("memorableMoment");
        assertThat(event.payload().get("topicSlots").get("event").asString())
                .isEqualTo("과일 자르기");
        assertThat(event.payload().get("topicSlots").get("emotion").isNull()).isTrue();
    }

    @Test
    void AI_TURN_COMPLETED를_역직렬화한다() {
        String json = """
                {
                  "eventId": "evt_internal_004",
                  "eventType": "AI_TURN_COMPLETED",
                  "conversationId": "conv_01JZ123ABC",
                  "realtimeSessionId": "rt_01JZ456DEF",
                  "sessionEpoch": 1,
                  "turnId": "turn_003",
                  "turnSeq": 3,
                  "responseId": "resp_003",
                  "occurredAt": "2026-07-22T10:00:34+00:00",
                  "payload": {
                    "text": "그때 어떤 기분이 들었어?",
                    "audioStartedAt": "2026-07-22T10:00:32+00:00",
                    "audioCompletedAt": "2026-07-22T10:00:34+00:00"
                  }
                }
                """;

        ConversationStreamEvent event = objectMapper.readValue(json, ConversationStreamEvent.class);

        assertThat(event.eventType()).isEqualTo(ConversationStreamEventType.AI_TURN_COMPLETED);
        assertThat(event.payload().get("audioStartedAt").asString())
                .isEqualTo("2026-07-22T10:00:32+00:00");
    }

    @Test
    void deserializesAiTurnInterruptedPayload() {
        String json = """
                {
                  "eventId": "evt_internal_005",
                  "eventType": "AI_TURN_INTERRUPTED",
                  "conversationId": "conv_01JZ123ABC",
                  "realtimeSessionId": "rt_01JZ456DEF",
                  "sessionEpoch": 1,
                  "turnId": "turn_003",
                  "turnSeq": 3,
                  "responseId": "resp_003",
                  "occurredAt": "2026-07-22T10:00:33+00:00",
                  "payload": {
                    "interruptedAt": "2026-07-22T10:00:33+00:00",
                    "reason": "CHILD_BARGE_IN",
                    "interruptionId": "interrupt_003"
                  }
                }
                """;

        ConversationStreamEvent event = objectMapper.readValue(json, ConversationStreamEvent.class);
        AiTurnInterruptedPayload payload =
                objectMapper.treeToValue(event.payload(), AiTurnInterruptedPayload.class);

        assertThat(event.eventType()).isEqualTo(ConversationStreamEventType.AI_TURN_INTERRUPTED);
        assertThat(payload.interruptedAt())
                .isEqualTo(OffsetDateTime.parse("2026-07-22T10:00:33+00:00"));
        assertThat(payload.reason()).isEqualTo("CHILD_BARGE_IN");
        assertThat(payload.interruptionId()).isEqualTo("interrupt_003");
    }

    @Test
    void SESSION_이벤트는_턴_식별자_없이_역직렬화된다() {
        String started = """
                {
                  "eventId": "evt_internal_001",
                  "eventType": "SESSION_STARTED",
                  "conversationId": "conv_01JZ123ABC",
                  "realtimeSessionId": "rt_01JZ456DEF",
                  "sessionEpoch": 1,
                  "turnId": null,
                  "turnSeq": null,
                  "responseId": null,
                  "occurredAt": "2026-07-22T10:00:10+00:00",
                  "payload": { "connectedAt": "2026-07-22T10:00:10+00:00" }
                }
                """;
        String completed = """
                {
                  "eventId": "evt_internal_050",
                  "eventType": "SESSION_COMPLETED",
                  "conversationId": "conv_01JZ123ABC",
                  "realtimeSessionId": "rt_01JZ456DEF",
                  "sessionEpoch": 1,
                  "turnId": null,
                  "turnSeq": null,
                  "responseId": null,
                  "occurredAt": "2026-07-22T10:08:00+00:00",
                  "payload": { "reason": "USER_FINISHED", "completedAt": "2026-07-22T10:08:00+00:00" }
                }
                """;

        ConversationStreamEvent startedEvent =
                objectMapper.readValue(started, ConversationStreamEvent.class);
        ConversationStreamEvent completedEvent =
                objectMapper.readValue(completed, ConversationStreamEvent.class);

        assertThat(startedEvent.eventType()).isEqualTo(ConversationStreamEventType.SESSION_STARTED);
        assertThat(startedEvent.turnId()).isNull();
        assertThat(startedEvent.turnSeq()).isNull();
        assertThat(completedEvent.eventType())
                .isEqualTo(ConversationStreamEventType.SESSION_COMPLETED);
        assertThat(completedEvent.payload().get("reason").asString()).isEqualTo("USER_FINISHED");
    }
}
