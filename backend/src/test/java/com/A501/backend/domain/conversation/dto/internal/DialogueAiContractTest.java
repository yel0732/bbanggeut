package com.A501.backend.domain.conversation.dto.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.A501.backend.domain.conversation.entity.TerminationReason;
import tools.jackson.databind.ObjectMapper;

class DialogueAiContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 요청은_FastAPI의_camelCase_계약으로_직렬화된다() throws Exception {
        DialogueAiRequest request = new DialogueAiRequest(
                "req_contract",
                "conv_contract",
                "친구와 블록 놀이를 했어.",
                null,
                DialogueSlotsPayload.empty(),
                List.of(),
                null,
                0,
                6);

        String json = objectMapper.writeValueAsString(request);

        assertThat(json)
                .contains("\"requestId\":\"req_contract\"")
                .contains("\"conversationId\":\"conv_contract\"")
                .contains("\"childAnswer\":\"친구와 블록 놀이를 했어.\"")
                .contains("\"currentSlots\"")
                .contains("\"recentQuestions\":[]")
                .contains("\"questionCount\":0")
                .contains("\"maxQuestionCount\":6");
    }

    @Test
    void FastAPI의_종료_응답을_역직렬화한다() throws Exception {
        DialogueAiResponse response = objectMapper.readValue(
                """
                {
                  "updatedSlots": {
                    "event": "블록 놀이",
                    "people": ["친구"],
                    "place": null,
                    "emotion": "즐거움",
                    "emotionReason": null,
                    "memorableMoment": "성을 완성한 순간",
                    "result": "함께 정리함"
                  },
                  "decision": "TERMINATE",
                  "terminationReason": "REQUIRED_SLOTS_FILLED",
                  "assistantQuestion": null,
                  "questionGenerationStatus": null,
                  "questionGenerationSource": null,
                  "questionGenerationFailureCode": null
                }
                """,
                DialogueAiResponse.class);

        assertThat(response.decision()).isEqualTo(
                DialogueAiDecision.TERMINATE);
        assertThat(response.terminationReason()).isEqualTo(
                TerminationReason.REQUIRED_SLOTS_FILLED);
        assertThat(response.updatedSlots().people())
                .containsExactly("친구");
    }
}
