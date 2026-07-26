package com.A501.backend.domain.conversation.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.conversation.dto.internal.DialogueAiDecision;
import com.A501.backend.domain.conversation.dto.internal.DialogueAiResponse;
import com.A501.backend.domain.conversation.dto.internal.DialogueSlotsPayload;
import com.A501.backend.domain.conversation.dto.response.TextTurnResponse;
import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.ConversationStatus;
import com.A501.backend.domain.conversation.entity.ConversationTopic;
import com.A501.backend.domain.conversation.entity.ConversationTopicState;
import com.A501.backend.domain.conversation.entity.ConversationTurn;
import com.A501.backend.domain.conversation.entity.Speaker;
import com.A501.backend.domain.conversation.entity.TurnStatus;
import com.A501.backend.domain.conversation.exception.ConversationErrorCode;
import com.A501.backend.domain.conversation.exception.ConversationException;
import com.A501.backend.domain.conversation.repository.ConversationSessionRepository;
import com.A501.backend.domain.conversation.repository.ConversationTopicStateRepository;
import com.A501.backend.domain.conversation.repository.ConversationTurnRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional(readOnly = true)
public class ConversationTextTurnPersistenceService {

    private final ConversationSessionRepository conversationSessionRepository;
    private final ConversationTurnRepository conversationTurnRepository;
    private final ConversationTopicStateRepository topicStateRepository;
    private final ChildAccessVerifier childAccessVerifier;
    private final CurrentMemberProvider currentMemberProvider;
    private final ObjectMapper objectMapper;
    private final Clock utcClock;

    public ConversationTextTurnPersistenceService(
            ConversationSessionRepository conversationSessionRepository,
            ConversationTurnRepository conversationTurnRepository,
            ConversationTopicStateRepository topicStateRepository,
            ChildAccessVerifier childAccessVerifier,
            CurrentMemberProvider currentMemberProvider,
            ObjectMapper objectMapper,
            @Qualifier("utcClock") Clock utcClock) {
        this.conversationSessionRepository = conversationSessionRepository;
        this.conversationTurnRepository = conversationTurnRepository;
        this.topicStateRepository = topicStateRepository;
        this.childAccessVerifier = childAccessVerifier;
        this.currentMemberProvider = currentMemberProvider;
        this.objectMapper = objectMapper;
        this.utcClock = utcClock;
    }

    public PreparedTextTurn prepare(String conversationId) {
        ConversationSession session = findConversation(conversationId);
        verifyConversationAccess(session);
        validateActive(session);

        List<ConversationTopicState> topics =
                topicStateRepository
                        .findAllByConversationIdOrderByTopicOrderAsc(conversationId);
        ConversationTopicState currentTopic = topics.stream()
                .filter(topic -> topic.getTopicType() == session.getCurrentTopic())
                .findFirst()
                .orElseThrow(() -> new ConversationException(
                        ConversationErrorCode.DIALOGUE_STATE_INVALID));
        ConversationTopic nextTopic = topics.stream()
                .filter(topic -> topic.getTopicOrder() > currentTopic.getTopicOrder())
                .findFirst()
                .map(ConversationTopicState::getTopicType)
                .orElse(null);
        DialogueSlotsPayload slots = deserializeSlots(currentTopic.getSlotsJson());
        List<ConversationTurn> recentAiTurns =
                conversationTurnRepository
                        .findTop5ByConversationIdAndTopicTypeAndSpeakerAndStatusOrderByTurnSeqDesc(
                                conversationId,
                                currentTopic.getTopicType(),
                                Speaker.AI,
                                TurnStatus.COMPLETED);
        String currentQuestion = recentAiTurns.isEmpty()
                ? null
                : resolveQuestionText(recentAiTurns.get(0));
        List<String> recentQuestions = new ArrayList<>(
                recentAiTurns.stream()
                        .map(this::resolveQuestionText)
                        .toList());
        Collections.reverse(recentQuestions);

        return new PreparedTextTurn(
                conversationId,
                session.getVersion(),
                currentTopic.getVersion(),
                currentTopic.getTopicType(),
                nextTopic,
                slots,
                deserializeContext(currentTopic.getContextJson()),
                currentQuestion,
                recentQuestions,
                currentTopic.getQuestionCount(),
                currentTopic.getMaxQuestionCount());
    }

    @Transactional
    public TextTurnResponse finalizeTurn(
            PreparedTextTurn prepared,
            String childAnswer,
            DialogueAiResponse aiResponse) {
        ConversationSession session = conversationSessionRepository
                .findByIdForUpdate(prepared.conversationId())
                .orElseThrow(() -> new ConversationException(
                        ConversationErrorCode.CONVERSATION_NOT_FOUND));
        verifyConversationAccess(session);
        validateActive(session);
        if (!Objects.equals(prepared.sessionVersion(), session.getVersion())) {
            throw new ConversationException(
                    ConversationErrorCode.CONVERSATION_TURN_CONFLICT);
        }
        ConversationTopicState currentTopic = topicStateRepository
                .findByConversationIdAndTopicTypeForUpdate(
                        prepared.conversationId(),
                        prepared.currentTopic())
                .orElseThrow(() -> new ConversationException(
                        ConversationErrorCode.DIALOGUE_STATE_INVALID));
        if (!Objects.equals(prepared.topicVersion(), currentTopic.getVersion())
                || session.getCurrentTopic() != prepared.currentTopic()) {
            throw new ConversationException(
                    ConversationErrorCode.CONVERSATION_TURN_CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now(utcClock)
                .truncatedTo(ChronoUnit.MICROS);
        if (session.getStatus() == ConversationStatus.READY) {
            session.start(now);
        }
        if (currentTopic.getStatus()
                == com.A501.backend.domain.conversation.entity.ConversationTopicStatus.PENDING) {
            currentTopic.start(now);
        }

        int childTurnSeq =
                conversationTurnRepository.findMaxTurnSeqByConversationId(
                        session.getConversationId()) + 1;
        ConversationTurn childTurn = ConversationTurn.createChildCompleted(
                generateId("turn_"),
                session.getConversationId(),
                childTurnSeq,
                childAnswer,
                null,
                now,
                now,
                prepared.currentTopic());
        conversationTurnRepository.save(childTurn);

        currentTopic.updateSlots(serializeSlots(aiResponse.updatedSlots()));

        ConversationTurn assistantTurn = null;
        if (aiResponse.decision() == DialogueAiDecision.CONTINUE_TOPIC) {
            assistantTurn = ConversationTurn.createAiGenerating(
                    generateId("turn_"),
                    session.getConversationId(),
                    childTurnSeq + 1,
                    aiResponse.assistantMessage(),
                    aiResponse.assistantQuestion(),
                    generateId("resp_"),
                    now,
                    prepared.currentTopic());
            assistantTurn.complete(now);
            conversationTurnRepository.save(assistantTurn);
            currentTopic.incrementQuestionCount();
            session.incrementQuestionCount();
        } else if (aiResponse.decision() == DialogueAiDecision.SWITCH_TOPIC) {
            if (prepared.nextTopic() == null) {
                throw new ConversationException(
                        ConversationErrorCode.DIALOGUE_STATE_INVALID);
            }
            currentTopic.complete(now, aiResponse.terminationReason());
            ConversationTopicState nextTopic = topicStateRepository
                    .findByConversationIdAndTopicTypeForUpdate(
                            prepared.conversationId(),
                            prepared.nextTopic())
                    .orElseThrow(() -> new ConversationException(
                            ConversationErrorCode.DIALOGUE_STATE_INVALID));
            nextTopic.start(now);
            session.switchTopic(prepared.nextTopic());
            assistantTurn = ConversationTurn.createAiGenerating(
                    generateId("turn_"),
                    session.getConversationId(),
                    childTurnSeq + 1,
                    aiResponse.assistantMessage(),
                    aiResponse.assistantQuestion(),
                    generateId("resp_"),
                    now,
                    prepared.nextTopic());
            assistantTurn.complete(now);
            conversationTurnRepository.save(assistantTurn);
            nextTopic.incrementQuestionCount();
            session.incrementQuestionCount();
        } else {
            currentTopic.complete(now, aiResponse.terminationReason());
            session.terminate(now, aiResponse.terminationReason());
        }

        return TextTurnResponse.of(
                childTurn,
                assistantTurn,
                session.getStatus());
    }

    private ConversationSession findConversation(String conversationId) {
        return conversationSessionRepository.findById(conversationId)
                .orElseThrow(() -> new ConversationException(
                        ConversationErrorCode.CONVERSATION_NOT_FOUND));
    }

    private void validateActive(ConversationSession session) {
        if (session.getStatus().isTerminal()) {
            throw new ConversationException(
                    ConversationErrorCode.INVALID_SESSION_STATE);
        }
    }

    private void verifyConversationAccess(ConversationSession session) {
        try {
            childAccessVerifier.verifyAccess(
                    currentMemberProvider.getCurrentMemberId(),
                    session.getChildId());
        } catch (ConversationException exception) {
            if (exception.getErrorCode()
                    == ConversationErrorCode.CHILD_ACCESS_DENIED) {
                throw new ConversationException(
                        ConversationErrorCode.CONVERSATION_ACCESS_DENIED);
            }
            if (exception.getErrorCode()
                    == ConversationErrorCode.CHILD_NOT_FOUND) {
                throw new ConversationException(
                        ConversationErrorCode.CONVERSATION_NOT_FOUND);
            }
            throw exception;
        }
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

    private com.A501.backend.domain.conversation.dto.internal.DialogueTopicContextPayload
            deserializeContext(String contextJson) {
        try {
            return objectMapper.readValue(
                    contextJson,
                    com.A501.backend.domain.conversation.dto.internal.DialogueTopicContextPayload.class);
        } catch (JacksonException exception) {
            throw new ConversationException(
                    ConversationErrorCode.DIALOGUE_STATE_INVALID);
        }
    }

    private String serializeSlots(DialogueSlotsPayload slots) {
        try {
            return objectMapper.writeValueAsString(slots);
        } catch (JacksonException exception) {
            throw new ConversationException(
                    ConversationErrorCode.DIALOGUE_STATE_INVALID);
        }
    }

    private String generateId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }

    private String resolveQuestionText(ConversationTurn turn) {
        String questionText = turn.getQuestionText();
        return questionText == null || questionText.isBlank()
                ? turn.getContent()
                : questionText;
    }
}
