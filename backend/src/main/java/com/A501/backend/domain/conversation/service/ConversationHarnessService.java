package com.A501.backend.domain.conversation.service;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.conversation.dto.internal.DialogueSlotsPayload;
import com.A501.backend.domain.conversation.dto.response.ConversationHarnessStateResponse;
import com.A501.backend.domain.conversation.dto.response.ConversationSessionResponse;
import com.A501.backend.domain.conversation.dto.response.CreateConversationResponse;
import com.A501.backend.domain.conversation.entity.ConversationStatus;
import com.A501.backend.domain.conversation.exception.ConversationErrorCode;
import com.A501.backend.domain.conversation.exception.ConversationException;
import com.A501.backend.domain.conversation.repository.ConversationSessionRepository;
import com.A501.backend.domain.conversation.repository.ConversationTopicStateRepository;

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
    private final ConversationTopicStateRepository topicStateRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CreateConversationResponse createOrResume(Long childId) {
        try {
            return conversationSessionService.create(childId);
        } catch (ConversationException exception) {
            if (exception.getErrorCode()
                    != ConversationErrorCode.ACTIVE_CONVERSATION_EXISTS) {
                throw exception;
            }
            return conversationSessionRepository
                    .findFirstByChildIdAndStatusInOrderByCreatedAtDesc(
                            childId,
                            ACTIVE_STATUSES)
                    .map(session -> CreateConversationResponse.from(
                            session,
                            topicStateRepository
                                    .findAllByConversationIdOrderByTopicOrderAsc(
                                            session.getConversationId())))
                    .orElseThrow(() -> exception);
        }
    }

    public ConversationHarnessStateResponse getState(String conversationId) {
        ConversationSessionResponse session =
                conversationSessionService.getConversation(conversationId);
        DialogueSlotsPayload slots = topicStateRepository
                .findByConversationIdAndTopicType(
                        conversationId,
                        session.currentTopic())
                .map(topic -> deserializeSlots(topic.getSlotsJson()))
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
