package com.A501.backend.domain.conversation.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.conversation.dto.internal.DialogueAiDecision;
import com.A501.backend.domain.conversation.dto.internal.DialogueAiResponse;
import com.A501.backend.domain.conversation.dto.internal.DialogueOpeningResponse;
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
        List<ConversationTurn> completedTopicTurns =
                conversationTurnRepository
                        .findByConversationIdAndTopicTypeAndStatusOrderByTurnSeqAsc(
                                conversationId,
                                currentTopic.getTopicType(),
                                TurnStatus.COMPLETED);
        List<ConversationTurn> topicAiTurns = completedTopicTurns.stream()
                .filter(turn -> turn.getSpeaker() == Speaker.AI)
                .toList();
        String currentQuestion = topicAiTurns.isEmpty()
                ? null
                : DialogueTopicHistoryAssembler.resolveQuestionText(
                        topicAiTurns.get(topicAiTurns.size() - 1));
        String currentTarget = topicAiTurns.isEmpty()
                ? null
                : DialogueTopicHistoryAssembler.resolveSupportedQuestionTarget(
                        topicAiTurns.get(topicAiTurns.size() - 1));
        List<String> allQuestions = topicAiTurns.stream()
                .map(DialogueTopicHistoryAssembler::resolveQuestionText)
                .toList();
        List<String> recentQuestions = allQuestions.subList(
                Math.max(0, allQuestions.size() - 5),
                allQuestions.size());

        return new PreparedTextTurn(
                conversationId,
                session.getVersion(),
                currentTopic.getVersion(),
                currentTopic.getTopicType(),
                nextTopic,
                slots,
                deserializeContext(currentTopic.getContextJson()),
                currentQuestion,
                currentTarget,
                deserializeSkippedTargets(currentTopic.getSkippedTargetsJson()),
                recentQuestions,
                DialogueTopicHistoryAssembler.completedExchanges(
                        completedTopicTurns),
                currentTopic.getQuestionCount(),
                currentTopic.getMaxQuestionCount());
    }

    @Transactional
    public void finalizeOpening(
            PreparedTextTurn prepared,
            DialogueOpeningResponse opening) {
        ConversationSession session = conversationSessionRepository
                .findByIdForUpdate(prepared.conversationId())
                .orElseThrow(() -> new ConversationException(
                        ConversationErrorCode.CONVERSATION_NOT_FOUND));
        verifyConversationAccess(session);
        validateActive(session);

        int maxTurnSeq = conversationTurnRepository
                .findMaxTurnSeqByConversationId(prepared.conversationId());
        if (maxTurnSeq > 0 || session.getQuestionCount() > 0) {
            return;
        }
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

        ConversationTurn assistantTurn = ConversationTurn.createAiGenerating(
                generateId("turn_"),
                session.getConversationId(),
                1,
                opening.assistantMessage(),
                opening.assistantQuestion(),
                opening.assistantTarget(),
                generateId("resp_"),
                now,
                prepared.currentTopic());
        assistantTurn.complete(now);
        conversationTurnRepository.save(assistantTurn);
        currentTopic.incrementQuestionCount();
        session.incrementQuestionCount();
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
        if (aiResponse.updatedSkippedTargets() != null) {
            currentTopic.updateSkippedTargets(
                    serializeSkippedTargets(aiResponse.updatedSkippedTargets()));
        }

        ConversationTurn assistantTurn = null;
        if (aiResponse.decision() == DialogueAiDecision.CONTINUE_TOPIC) {
            assistantTurn = ConversationTurn.createAiGenerating(
                    generateId("turn_"),
                    session.getConversationId(),
                    childTurnSeq + 1,
                    aiResponse.assistantMessage(),
                    aiResponse.assistantQuestion(),
                    aiResponse.assistantTarget(),
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
            // 아이가 전환하면서 새 주제 이야기를 이미 꺼냈다면 그 내용을 첫 슬롯으로
            // 넣는다. 버리면 방금 말한 사건을 새 주제 첫 질문에서 다시 묻게 된다.
            if (aiResponse.nextTopicSlots() != null) {
                nextTopic.updateSlots(serializeSlots(aiResponse.nextTopicSlots()));
            }
            session.switchTopic(prepared.nextTopic());
            assistantTurn = ConversationTurn.createAiGenerating(
                    generateId("turn_"),
                    session.getConversationId(),
                    childTurnSeq + 1,
                    aiResponse.assistantMessage(),
                    aiResponse.assistantQuestion(),
                    aiResponse.assistantTarget(),
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
                session.getStatus(),
                aiResponse.closingMessage());
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

    private List<String> deserializeSkippedTargets(String skippedTargetsJson) {
        try {
            List<String> targets = List.of(objectMapper.readValue(
                    skippedTargetsJson,
                    String[].class));
            return DialogueTopicHistoryAssembler.normalizeSupportedQuestionTargets(targets);
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new ConversationException(
                    ConversationErrorCode.DIALOGUE_STATE_INVALID);
        }
    }

    private String serializeSkippedTargets(List<String> skippedTargets) {
        try {
            return objectMapper.writeValueAsString(
                    DialogueTopicHistoryAssembler.normalizeSupportedQuestionTargets(
                            skippedTargets));
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new ConversationException(
                    ConversationErrorCode.DIALOGUE_STATE_INVALID);
        }
    }

    private String generateId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }

}
