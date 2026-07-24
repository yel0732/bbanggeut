package com.A501.backend.domain.conversation.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.A501.backend.domain.conversation.dto.internal.DialogueAiRequest;
import com.A501.backend.domain.conversation.dto.internal.DialogueAiResponse;
import com.A501.backend.domain.conversation.dto.request.CreateTextTurnRequest;
import com.A501.backend.domain.conversation.dto.response.TextTurnResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConversationTextTurnService {

    private final ConversationTextTurnPersistenceService persistenceService;
    private final DialogueAiClient dialogueAiClient;

    public TextTurnResponse createTextTurn(
            String conversationId,
            CreateTextTurnRequest request) {
        PreparedTextTurn prepared = persistenceService.prepare(conversationId);
        DialogueAiResponse aiResponse = dialogueAiClient.processTurn(
                new DialogueAiRequest(
                        generateRequestId(),
                        conversationId,
                        request.text(),
                        prepared.currentQuestion(),
                        prepared.currentSlots(),
                        prepared.recentQuestions(),
                        null,
                        prepared.questionCount(),
                        prepared.maxQuestionCount()));
        return persistenceService.finalizeTurn(
                prepared,
                request.text(),
                aiResponse);
    }

    private String generateRequestId() {
        return "req_" + UUID.randomUUID().toString().replace("-", "");
    }
}
