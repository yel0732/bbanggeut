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
import com.A501.backend.domain.conversation.dto.internal.DialogueSlotsPayload;
import com.A501.backend.domain.conversation.dto.request.CreateTextTurnRequest;

@ExtendWith(MockitoExtension.class)
class ConversationTextTurnServiceTest {

    @Mock
    private ConversationTextTurnPersistenceService persistenceService;

    @Mock
    private DialogueAiClient dialogueAiClient;

    @InjectMocks
    private ConversationTextTurnService service;

    @Test
    void 준비한_Snapshot과_아이_답변만_AI에_전달한다() {
        DialogueSlotsPayload slots = DialogueSlotsPayload.empty();
        PreparedTextTurn prepared = new PreparedTextTurn(
                "conv_test",
                3L,
                slots,
                "이전 질문?",
                List.of("이전 질문?"),
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
        assertThat(request.currentSlots()).isSameAs(slots);
        assertThat(request.questionCount()).isEqualTo(1);
        verify(persistenceService).finalizeTurn(
                prepared,
                "합성 답변",
                aiResponse);
    }
}
