package com.A501.backend.domain.conversation.dto.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.A501.backend.domain.conversation.entity.ConversationTopic;
import com.A501.backend.domain.conversation.entity.TerminationReason;

import tools.jackson.databind.ObjectMapper;

class DialogueAiContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 요청은_주제_단위_FastAPI_계약으로_직렬화된다() throws Exception {
        DialogueAiRequest request = new DialogueAiRequest(
                "req_contract",
                "conv_contract",
                "친구와 블록 놀이를 했어.",
                "어떤 일이 있었어?",
                "event",
                ConversationTopic.GAME,
                ConversationTopic.DAILY,
                DialogueSlotsPayload.empty(),
                List.of(),
                List.of(),
                new DialogueTopicContextPayload(
                        ConversationTopic.GAME,
                        List.of()),
                0,
                6);

        String json = objectMapper.writeValueAsString(request);

        assertThat(json)
                .contains("\"requestId\":\"req_contract\"")
                .contains("\"conversationId\":\"conv_contract\"")
                .contains("\"childAnswer\":\"친구와 블록 놀이를 했어.\"")
                .contains("\"currentTarget\":\"event\"")
                .contains("\"skippedTargets\":[]")
                .contains("\"currentTopic\":\"GAME\"")
                .contains("\"nextTopic\":\"DAILY\"")
                .contains("\"currentTopicSlots\"")
                .contains("\"recentQuestions\":[]")
                .contains("\"topicHistory\":[]")
                .contains("\"topicContext\"")
                .contains("\"topicQuestionCount\":0")
                .contains("\"topicMaxQuestionCount\":6")
                .doesNotContain("\"result\"");
    }

    @Test
    void FastAPI의_세션_종료_응답을_역직렬화한다() throws Exception {
        DialogueAiResponse response = objectMapper.readValue(
                """
                {
                  "updatedSlots": {
                    "event": "블록 놀이",
                    "people": ["친구"],
                    "place": null,
                    "emotion": "즐거움",
                    "emotionReason": null,
                    "memorableMoment": "성을 완성한 순간"
                  },
                  "updatedSkippedTargets": ["emotionReason"],
                  "decision": "TERMINATE_SESSION",
                  "terminationReason": "REQUIRED_SLOTS_FILLED",
                  "assistantMessage": null,
                  "assistantQuestion": null,
                  "questionGenerationStatus": null,
                  "questionGenerationSource": null,
                  "questionGenerationFailureCode": null,
                  "closingMessage": "오늘 이야기 들려줘서 고마워! 다음에 또 이야기하자."
                }
                """,
                DialogueAiResponse.class);

        assertThat(response.decision())
                .isEqualTo(DialogueAiDecision.TERMINATE_SESSION);
        assertThat(response.terminationReason())
                .isEqualTo(TerminationReason.REQUIRED_SLOTS_FILLED);
        assertThat(response.updatedSkippedTargets())
                .containsExactly("emotionReason");
        assertThat(response.closingMessage())
                .isEqualTo("오늘 이야기 들려줘서 고마워! 다음에 또 이야기하자.");
        assertThat(response.updatedSlots().people())
                .containsExactly("친구");
    }

    @Test
    void FastAPI의_주제_전환_응답을_역직렬화한다() throws Exception {
        DialogueAiResponse response = objectMapper.readValue(
                """
                {
                  "updatedSlots": {},
                  "decision": "SWITCH_TOPIC",
                  "terminationReason": "MAX_QUESTION_REACHED",
                  "assistantMessage": "게임 이야기를 들려줘서 고마워! 이번에는 오늘 있었던 다른 일도 이야기해 줄래?",
                  "assistantQuestion": "이번에는 오늘 있었던 다른 일도 이야기해 줄래?",
                  "assistantTarget": "event",
                  "questionGenerationStatus": "fallback",
                  "questionGenerationSource": "policy",
                  "questionGenerationFailureCode": null
                }
                """,
                DialogueAiResponse.class);

        assertThat(response.decision()).isEqualTo(DialogueAiDecision.SWITCH_TOPIC);
        assertThat(response.assistantQuestion())
                .isEqualTo("이번에는 오늘 있었던 다른 일도 이야기해 줄래?");
        assertThat(response.assistantTarget()).isEqualTo("event");
    }
}
