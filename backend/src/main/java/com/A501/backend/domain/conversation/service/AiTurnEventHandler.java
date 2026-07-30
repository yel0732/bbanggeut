package com.A501.backend.domain.conversation.service;

import java.time.LocalDateTime;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.A501.backend.domain.conversation.dto.stream.AiTurnCompletedPayload;
import com.A501.backend.domain.conversation.dto.stream.AiTurnGeneratedPayload;
import com.A501.backend.domain.conversation.dto.stream.ConversationStreamEvent;
import com.A501.backend.domain.conversation.dto.stream.ConversationStreamEventType;
import com.A501.backend.domain.conversation.dto.internal.DialogueSlotsPayload;
import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.ConversationStatus;
import com.A501.backend.domain.conversation.entity.ConversationTopic;
import com.A501.backend.domain.conversation.entity.ConversationTopicState;
import com.A501.backend.domain.conversation.entity.ConversationTopicStatus;
import com.A501.backend.domain.conversation.entity.ConversationTurn;
import com.A501.backend.domain.conversation.entity.Speaker;
import com.A501.backend.domain.conversation.entity.TerminationReason;
import com.A501.backend.domain.conversation.entity.TurnStatus;
import com.A501.backend.domain.conversation.repository.ConversationTopicStateRepository;
import com.A501.backend.domain.conversation.repository.ConversationTurnRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class AiTurnEventHandler implements ConversationEventHandler {

    private final ConversationTurnRepository turnRepository;
    private final ConversationTopicStateRepository topicStateRepository;
    private final ConversationEventPayloadReader payloadReader;
    private final ObjectMapper objectMapper;

    @Override
    public Set<ConversationStreamEventType> supportedTypes() {
        return Set.of(
                ConversationStreamEventType.AI_TURN_GENERATED,
                ConversationStreamEventType.AI_TURN_COMPLETED);
    }

    @Override
    public void handle(ConversationStreamEvent event, ConversationSession session) {
        switch (event.eventType()) {
            case AI_TURN_GENERATED -> handleGenerated(event, session);
            case AI_TURN_COMPLETED -> handleCompleted(event);
            default -> throw new ConversationEventContractException(
                    "Unsupported AI turn event type: " + event.eventType());
        }
    }

    private void handleGenerated(
            ConversationStreamEvent event,
            ConversationSession session) {
        String turnId = payloadReader.requireText(event.turnId(), "turnId");
        int turnSeq = payloadReader.requireTurnSeq(event.turnSeq());
        String responseId = payloadReader.requireText(event.responseId(), "responseId");
        AiTurnGeneratedPayload payload =
                payloadReader.read(event, AiTurnGeneratedPayload.class);
        String text = payloadReader.requireText(payload.text(), "payload.text");
        String questionText = payload.questionText() == null
                ? text
                : payloadReader.requireText(payload.questionText(), "payload.questionText");
        ConversationTopic turnTopic = parseTopic(payload.topicType(), "payload.topicType");
        ConversationTopic slotsTopic = payload.slotsTopicType() == null
                ? turnTopic
                : parseTopic(payload.slotsTopicType(), "payload.slotsTopicType");

        ConversationTurn existing = turnRepository.findById(turnId)
                .orElseGet(() -> turnRepository
                        .findByConversationIdAndTurnSeq(event.conversationId(), turnSeq)
                        .orElse(null));
        if (existing != null) {
            validateExisting(
                    existing,
                    turnId,
                    event.conversationId(),
                    turnSeq,
                    responseId,
                    text,
                    questionText,
                    payload.targetSlot(),
                    turnTopic);
            return;
        }
        int latestTurnSeq = turnRepository
                .findMaxTurnSeqByConversationIdAndSpeaker(
                        event.conversationId(),
                        Speaker.AI);
        if (turnSeq > latestTurnSeq) {
            synchronizeProgress(event, session, turnTopic, slotsTopic, payload);
        } else if (turnTopic != slotsTopic) {
            // 지연 도착한 일반 턴은 이력만 채우고 진행 상태를 후퇴시키지 않지만(정상),
            // 전환 이벤트가 지연 도착하면 전환은 유실되고 턴만 저장되는 부분 적용이
            // 된다. FastAPI 계약상 전환 이후 턴은 전환 처리 전에 적용될 수 없으므로
            // 이 상황 자체가 계약 위반이다.
            throw new ConversationEventContractException(
                    "Stale topic transition event cannot be applied.");
        }
        turnRepository.save(ConversationTurn.createAiGenerating(
                turnId,
                event.conversationId(),
                turnSeq,
                text,
                questionText,
                payload.targetSlot(),
                responseId,
                payloadReader.toUtc(event.occurredAt(), "occurredAt"),
                turnTopic));
    }

    private void handleCompleted(ConversationStreamEvent event) {
        String turnId = payloadReader.requireText(event.turnId(), "turnId");
        int turnSeq = payloadReader.requireTurnSeq(event.turnSeq());
        String responseId = payloadReader.requireText(event.responseId(), "responseId");
        AiTurnCompletedPayload payload =
                payloadReader.read(event, AiTurnCompletedPayload.class);
        payloadReader.requireText(payload.text(), "payload.text");
        LocalDateTime audioStartedAt = payloadReader.toUtc(
                payload.audioStartedAt(),
                "payload.audioStartedAt");
        LocalDateTime audioCompletedAt = payloadReader.toUtc(
                payload.audioCompletedAt(),
                "payload.audioCompletedAt");
        if (audioCompletedAt.isBefore(audioStartedAt)) {
            throw new ConversationEventContractException(
                    "payload.audioCompletedAt cannot precede payload.audioStartedAt.");
        }
        ConversationTurn turn = turnRepository.findByIdForUpdate(turnId)
                .orElseThrow(() -> new ConversationEventContractException(
                        "AI_TURN_GENERATED must be processed before AI_TURN_COMPLETED."));
        // 턴의 정체성은 식별자(turnId·conversationId·turnSeq·responseId)로 판정한다.
        // COMPLETED의 text는 GENERATED가 이미 저장한 content와 같아야 할 이유가 없다
        // (향후 TTS가 다른 텍스트를 실을 수 있음) — content/questionText는 이 턴의
        // 저장값을 그대로 넘겨 동일성 검사를 무력화한다(DIARY-3-W06 리뷰 finding ③).
        validateExisting(
                turn,
                turnId,
                event.conversationId(),
                turnSeq,
                responseId,
                turn.getContent(),
                turn.getQuestionText(),
                turn.getQuestionTarget(),
                turn.getTopicType());

        if (turn.getStatus() == TurnStatus.GENERATING) {
            turn.complete(audioCompletedAt);
        }
    }

    private void synchronizeProgress(
            ConversationStreamEvent event,
            ConversationSession session,
            ConversationTopic turnTopic,
            ConversationTopic slotsTopic,
            AiTurnGeneratedPayload payload) {
        if (turnTopic == slotsTopic && session.getCurrentTopic() != turnTopic) {
            throw new ConversationEventContractException(
                    "Topic transition event must be processed before this AI turn.");
        }
        if (payload.topicSlots() == null || !payload.topicSlots().isObject()) {
            throw new ConversationEventContractException(
                    "payload.topicSlots must be a JSON object.");
        }
        ConversationTopicState topicState = topicStateRepository
                .findByConversationIdAndTopicTypeForUpdate(
                        event.conversationId(),
                        slotsTopic)
                .orElseThrow(() -> new ConversationEventContractException(
                        "Conversation topic state does not exist."));
        try {
            DialogueSlotsPayload slots =
                    objectMapper.treeToValue(payload.topicSlots(), DialogueSlotsPayload.class);
            topicState.updateSlots(objectMapper.writeValueAsString(slots));
            if (payload.skippedTargets() != null) {
                topicState.updateSkippedTargets(objectMapper.writeValueAsString(
                        DialogueTopicHistoryAssembler.normalizeSupportedQuestionTargets(
                                payload.skippedTargets())));
            }
        } catch (JacksonException exception) {
            throw new ConversationEventContractException(
                    "payload.topicSlots cannot be serialized.",
                    exception);
        } catch (IllegalArgumentException exception) {
            throw new ConversationEventContractException(
                    "payload.skippedTargets contains an invalid target.",
                    exception);
        }

        LocalDateTime occurredAt =
                payloadReader.toUtc(event.occurredAt(), "occurredAt");
        if (session.getStatus() == ConversationStatus.READY) {
            session.start(occurredAt);
        }
        if (topicState.getStatus() == ConversationTopicStatus.PENDING) {
            topicState.start(occurredAt);
        }

        ConversationTopicState turnTopicState = topicState;
        if (turnTopic != slotsTopic) {
            if (session.getCurrentTopic() != slotsTopic) {
                throw new ConversationEventContractException(
                        "payload.slotsTopicType does not match the current topic.");
            }
            TerminationReason completionReason =
                    parseReason(payload.topicCompletionReason());
            turnTopicState = topicStateRepository
                    .findByConversationIdAndTopicTypeForUpdate(
                            event.conversationId(),
                            turnTopic)
                    .orElseThrow(() -> new ConversationEventContractException(
                            "Next conversation topic state does not exist."));
            // 전환은 순방향 한 단계만 허용한다. 이미 완료된 토픽으로 되돌아가는 전환을
            // 허용하면 두 토픽이 모두 COMPLETED인데 세션은 IN_PROGRESS인 복구 불가
            // 상태가 예외 없이 저장된다.
            if (turnTopicState.getStatus() == ConversationTopicStatus.COMPLETED) {
                throw new ConversationEventContractException(
                        "Topic transition targets an already completed topic.");
            }
            if (turnTopicState.getTopicOrder() != topicState.getTopicOrder() + 1) {
                throw new ConversationEventContractException(
                        "Topic transition must move to the next topic in order.");
            }
            topicState.complete(occurredAt, completionReason);
            if (turnTopicState.getStatus() == ConversationTopicStatus.PENDING) {
                turnTopicState.start(occurredAt);
            }
            session.switchTopic(turnTopic);
        } else if (payload.topicCompletionReason() != null) {
            throw new ConversationEventContractException(
                    "payload.topicCompletionReason is only valid for a topic switch.");
        }

        try {
            if (payload.topicQuestionCount() != null) {
                turnTopicState.synchronizeQuestionCount(payload.topicQuestionCount());
            }
            if (payload.sessionQuestionCount() != null) {
                session.synchronizeQuestionCount(payload.sessionQuestionCount());
            }
        } catch (IllegalArgumentException exception) {
            throw new ConversationEventContractException(
                    "Question progress exceeds the configured limit.",
                    exception);
        }
    }

    private static ConversationTopic parseTopic(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ConversationEventContractException(fieldName + " is required.");
        }
        try {
            return ConversationTopic.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new ConversationEventContractException(
                    fieldName + " is invalid.",
                    exception);
        }
    }

    private static TerminationReason parseReason(String value) {
        try {
            return TerminationReason.from(value);
        } catch (IllegalArgumentException exception) {
            throw new ConversationEventContractException(
                    "payload.topicCompletionReason is invalid.",
                    exception);
        }
    }

    private static void validateExisting(
            ConversationTurn turn,
            String turnId,
            String conversationId,
            int turnSeq,
            String responseId,
            String text,
            String questionText,
            String questionTarget,
            ConversationTopic topic) {
        if (!turn.getTurnId().equals(turnId)
                || !turn.getConversationId().equals(conversationId)
                || turn.getTurnSeq() != turnSeq
                || turn.getSpeaker() != Speaker.AI
                || !responseId.equals(turn.getResponseId())
                || !text.equals(turn.getContent())
                || !questionText.equals(turn.getQuestionText())
                || !java.util.Objects.equals(
                        questionTarget,
                        turn.getQuestionTarget())
                || turn.getTopicType() != topic) {
            throw new ConversationEventContractException(
                    "turnId is already bound to a different AI turn.");
        }
    }
}
