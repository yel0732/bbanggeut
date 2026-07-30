package com.A501.backend.domain.conversation.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.A501.backend.domain.conversation.dto.internal.DialogueAiRequest;
import com.A501.backend.domain.conversation.dto.internal.DialogueAiResponse;
import com.A501.backend.domain.conversation.dto.internal.DialogueOpeningRequest;
import com.A501.backend.domain.conversation.dto.internal.DialogueOpeningResponse;
import com.A501.backend.domain.conversation.dto.request.CreateTextTurnRequest;
import com.A501.backend.domain.conversation.dto.response.TextTurnResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConversationTextTurnService {

    private final ConversationTextTurnPersistenceService persistenceService;
    private final DialogueAiClient dialogueAiClient;

    public void ensureConversationOpened(String conversationId) {
        PreparedTextTurn prepared = persistenceService.prepare(conversationId);
        if (prepared.currentQuestion() != null
                || prepared.topicQuestionCount() > 0) {
            return;
        }
        DialogueOpeningResponse opening = dialogueAiClient.openConversation(
                new DialogueOpeningRequest(
                        prepared.currentTopic(),
                        prepared.topicContext()));
        persistenceService.finalizeOpening(prepared, opening);
    }

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
                        prepared.currentTarget(),
                        prepared.skippedTargets(),
                        prepared.currentTopic(),
                        prepared.nextTopic(),
                        prepared.currentTopicSlots(),
                        prepared.recentQuestions(),
                        prepared.topicHistory(),
                        prepared.topicContext(),
                        prepared.topicQuestionCount(),
                        prepared.topicMaxQuestionCount()));
        return persistenceService.finalizeTurn(
                prepared,
                request.text(),
                aiResponse);
    }

    private String generateRequestId() {
        return "req_" + UUID.randomUUID().toString().replace("-", "");
    }
}
