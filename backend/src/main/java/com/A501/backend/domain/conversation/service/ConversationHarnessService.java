package com.A501.backend.domain.conversation.service;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.conversation.dto.internal.DialogueSlotsPayload;
import com.A501.backend.domain.conversation.dto.request.CreateConversationRequest;
import com.A501.backend.domain.conversation.dto.response.ConversationHarnessStateResponse;
import com.A501.backend.domain.conversation.dto.response.ConversationSessionResponse;
import com.A501.backend.domain.conversation.dto.response.CreateConversationResponse;
import com.A501.backend.domain.conversation.entity.ConversationStatus;
import com.A501.backend.domain.conversation.exception.ConversationErrorCode;
import com.A501.backend.domain.conversation.exception.ConversationException;
import com.A501.backend.domain.conversation.repository.ConversationDialogueStateRepository;
import com.A501.backend.domain.conversation.repository.ConversationSessionRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@Profile("local")
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ConversationHarnessService {

    private static final Set<ConversationStatus> ACTIVE_STATUSES =
            EnumSet.of(ConversationStatus.READY, ConversationStatus.IN_PROGRESS);

    private final ConversationSessionService conversationSessionService;
    private final ConversationSessionRepository conversationSessionRepository;
    private final ConversationDialogueStateRepository dialogueStateRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CreateConversationResponse createOrResume(
            Long childId,
            CreateConversationRequest request) {
        try {
            return conversationSessionService.create(childId, request);
        } catch (ConversationException exception) {
            if (exception.getErrorCode()
                    != ConversationErrorCode.ACTIVE_CONVERSATION_EXISTS) {
                throw exception;
            }
            return conversationSessionRepository
                    .findFirstByChildIdAndStatusInOrderByCreatedAtDesc(
                            childId,
                            ACTIVE_STATUSES)
                    .map(CreateConversationResponse::from)
                    .orElseThrow(() -> exception);
        }
    }

    public ConversationHarnessStateResponse getState(String conversationId) {
        ConversationSessionResponse session =
                conversationSessionService.getConversation(conversationId);
        DialogueSlotsPayload slots = dialogueStateRepository
                .findById(conversationId)
                .map(state -> deserializeSlots(state.getSlotsJson()))
                .orElseGet(DialogueSlotsPayload::empty);
        return new ConversationHarnessStateResponse(session, slots);
    }

    private DialogueSlotsPayload deserializeSlots(String slotsJson) {
        try {
            return objectMapper.readValue(
                    slotsJson,
                    DialogueSlotsPayload.class);
        } catch (JacksonException exception) {
            throw new ConversationException(
                    ConversationErrorCode.DIALOGUE_STATE_INVALID);
        }
    }
}
