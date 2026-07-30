package com.A501.backend.domain.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.A501.backend.domain.conversation.dto.internal.DialogueAiDecision;
import com.A501.backend.domain.conversation.dto.internal.DialogueAiRequest;
import com.A501.backend.domain.conversation.dto.internal.DialogueAiResponse;
import com.A501.backend.domain.conversation.dto.internal.DialogueOpeningRequest;
import com.A501.backend.domain.conversation.dto.internal.DialogueOpeningResponse;
import com.A501.backend.domain.conversation.dto.internal.DialogueSlotsPayload;
import com.A501.backend.domain.conversation.dto.internal.DialogueTopicContextPayload;
import com.A501.backend.domain.conversation.dto.internal.DialogueTopicHistoryPayload;
import com.A501.backend.domain.conversation.dto.request.CreateTextTurnRequest;
import com.A501.backend.domain.conversation.entity.ConversationTopic;

@ExtendWith(MockitoExtension.class)
class ConversationTextTurnServiceTest {

    @Mock
    private ConversationTextTurnPersistenceService persistenceService;

    @Mock
    private DialogueAiClient dialogueAiClient;

    @InjectMocks
    private ConversationTextTurnService service;

    @Test
    void WebRTC와_같은_첫_질문을_AI_첫_턴으로_저장한다() {
        PreparedTextTurn prepared = new PreparedTextTurn(
                "conv_open",
                1L,
                DialogueSlotsPayload.empty(),
                null,
                List.of(),
                0,
                6);
        DialogueOpeningResponse opening = new DialogueOpeningResponse(
                "오늘 가장 기억에 남는 일은 무엇이었어?",
                "오늘 가장 기억에 남는 일은 무엇이었어?",
                "event");
        when(persistenceService.prepare("conv_open")).thenReturn(prepared);
        when(dialogueAiClient.openConversation(any())).thenReturn(opening);

        service.ensureConversationOpened("conv_open");

        ArgumentCaptor<DialogueOpeningRequest> requestCaptor =
                ArgumentCaptor.forClass(DialogueOpeningRequest.class);
        verify(dialogueAiClient).openConversation(requestCaptor.capture());
        assertThat(requestCaptor.getValue().currentTopic())
                .isEqualTo(prepared.currentTopic());
        assertThat(requestCaptor.getValue().topicContext())
                .isEqualTo(prepared.topicContext());
        verify(persistenceService).finalizeOpening(prepared, opening);
    }

    @Test
    void 준비한_Snapshot과_아이_답변만_AI에_전달한다() {
        DialogueSlotsPayload slots = DialogueSlotsPayload.empty();
        List<DialogueTopicHistoryPayload> history = List.of(
                new DialogueTopicHistoryPayload(
                        "블록을 만들었구나! 기분이 어땠어?",
                        "기분이 어땠어?",
                        "신났어"));
        PreparedTextTurn prepared = new PreparedTextTurn(
                "conv_test",
                3L,
                0L,
                ConversationTopic.DAILY,
                null,
                slots,
                DialogueTopicContextPayload.empty(ConversationTopic.DAILY),
                "이전 질문?",
                "emotion",
                List.of("이전 질문?"),
                history,
                1,
                6);
        DialogueAiResponse aiResponse = new DialogueAiResponse(
                slots,
                DialogueAiDecision.CONTINUE,
                null,
                "다음 질문?",
                "generated",
                "llm",
                null);
        when(persistenceService.prepare("conv_test")).thenReturn(prepared);
        when(dialogueAiClient.processTurn(any())).thenReturn(aiResponse);

        service.createTextTurn(
                "conv_test",
                new CreateTextTurnRequest("  합성 답변  "));

        ArgumentCaptor<DialogueAiRequest> requestCaptor =
                ArgumentCaptor.forClass(DialogueAiRequest.class);
        verify(dialogueAiClient).processTurn(requestCaptor.capture());
        DialogueAiRequest request = requestCaptor.getValue();
        assertThat(request.childAnswer()).isEqualTo("합성 답변");
        assertThat(request.currentQuestion()).isEqualTo("이전 질문?");
        assertThat(request.currentTarget()).isEqualTo("emotion");
        assertThat(request.topicHistory()).isEqualTo(history);
        assertThat(request.currentSlots()).isSameAs(slots);
        assertThat(request.questionCount()).isEqualTo(1);
        verify(persistenceService).finalizeTurn(
                prepared,
                "합성 답변",
                aiResponse);
    }
}
