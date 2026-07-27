package com.A501.backend.domain.conversation.dto.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.A501.backend.domain.conversation.dto.internal.DialogueGameContextPayload;
import com.A501.backend.domain.conversation.dto.internal.DialogueSlotsPayload;
import com.A501.backend.domain.conversation.entity.ConversationGoal;
import com.A501.backend.domain.conversation.entity.ConversationTopic;

import tools.jackson.databind.ObjectMapper;

class ContextSnapshotContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void Context_Snapshot은_realtime_context_계약으로_직렬화된다() throws Exception {
        ContextSnapshot snapshot = ContextSnapshot.create(
                "conv_01JZ123ABC",
                new RealtimeChildContext("하준", "6_9", List.of("공룡", "블록")),
                new RealtimeConversationContext(
                        ConversationGoal.DIARY, "ko-KR", ConversationTopic.GAME, 12),
                null,
                List.of(
                        new RealtimeTopicSnapshot(
                                ConversationTopic.GAME,
                                1,
                                6,
                                List.of(new DialogueGameContextPayload(
                                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                                        1L,
                                        820,
                                        180,
                                        LocalDateTime.of(2026, 7, 27, 10, 0))),
                                DialogueSlotsPayload.empty()),
                        RealtimeTopicSnapshot.withoutGames(ConversationTopic.DAILY, 2, 6)),
                RealtimePolicySnapshot.defaultPolicy());

        String json = objectMapper.writeValueAsString(snapshot);

        assertThat(json)
                .contains("\"contextVersion\":1")
                .contains("\"conversationId\":\"conv_01JZ123ABC\"")
                .contains("\"displayName\":\"하준\"")
                .contains("\"ageBand\":\"6_9\"")
                .contains("\"interests\":[\"공룡\",\"블록\"]")
                .contains("\"goal\":\"DIARY\"")
                .contains("\"language\":\"ko-KR\"")
                .contains("\"currentTopic\":\"GAME\"")
                .contains("\"totalMaxQuestionCount\":12")
                .contains("\"maxQuestionCount\":6")
                .contains("\"oneQuestionAtATime\":true")
                .contains("\"avoidPressureQuestions\":true")
                .doesNotContain("birthDate")
                .doesNotContain("parentEmail");
    }

    @Test
    void 저장된_JSON을_Context_Snapshot으로_역직렬화한다() throws Exception {
        String json = """
                {
                  "contextVersion": 1,
                  "conversationId": "conv_01JZ123ABC",
                  "child": {
                    "displayName": "하준",
                    "ageBand": "6_9",
                    "interests": ["공룡", "블록"]
                  },
                  "conversation": {
                    "goal": "DIARY",
                    "language": "ko-KR",
                    "currentTopic": "DAILY",
                    "totalMaxQuestionCount": 6
                  },
                  "recentConversationSummary": null,
                  "topics": [
                    {
                      "type": "DAILY",
                      "order": 1,
                      "maxQuestionCount": 6,
                      "games": [],
                      "initialSlots": {
                        "event": null, "people": [], "place": null,
                        "emotion": null, "emotionReason": null, "memorableMoment": null
                      }
                    }
                  ],
                  "policy": {
                    "oneQuestionAtATime": true,
                    "avoidPressureQuestions": true
                  }
                }
                """;

        ContextSnapshot snapshot = objectMapper.readValue(json, ContextSnapshot.class);

        assertThat(snapshot.conversation().currentTopic()).isEqualTo(ConversationTopic.DAILY);
        assertThat(snapshot.topics()).hasSize(1);
        assertThat(snapshot.topics().get(0).initialSlots().people()).isEmpty();
    }
}
