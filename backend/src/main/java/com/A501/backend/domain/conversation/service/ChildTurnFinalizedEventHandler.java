package com.A501.backend.domain.conversation.service;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.A501.backend.domain.conversation.dto.stream.ChildTurnFinalizedPayload;
import com.A501.backend.domain.conversation.dto.stream.ConversationStreamEvent;
import com.A501.backend.domain.conversation.dto.stream.ConversationStreamEventType;
import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.ConversationTopic;
import com.A501.backend.domain.conversation.entity.ConversationTurn;
import com.A501.backend.domain.conversation.entity.Speaker;
import com.A501.backend.domain.conversation.entity.TurnStatus;
import com.A501.backend.domain.conversation.repository.ConversationTurnRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ChildTurnFinalizedEventHandler implements ConversationEventHandler {

    private final ConversationTurnRepository turnRepository;
    private final ConversationEventPayloadReader payloadReader;

    @Override
    public Set<ConversationStreamEventType> supportedTypes() {
        return Set.of(ConversationStreamEventType.CHILD_TURN_FINALIZED);
    }

    @Override
    public void handle(ConversationStreamEvent event, ConversationSession session) {
        String turnId = payloadReader.requireText(event.turnId(), "turnId");
        int turnSeq = payloadReader.requireTurnSeq(event.turnSeq());
        if (event.responseId() != null) {
            throw new ConversationEventContractException(
                    "responseId must be null for a child turn.");
        }
        ChildTurnFinalizedPayload payload =
                payloadReader.read(event, ChildTurnFinalizedPayload.class);
        String text = payloadReader.requireText(payload.text(), "payload.text");
        validateSttConfidence(payload.sttConfidence());
        LocalDateTime startedAt = payloadReader.toUtc(payload.startedAt(), "payload.startedAt");
        LocalDateTime completedAt = payloadReader.toUtc(payload.completedAt(), "payload.completedAt");
        ConversationTopic topic = parseTopic(payload.topicType(), session.getCurrentTopic());

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
                    text,
                    payload.sttConfidence(),
                    startedAt,
                    completedAt,
                    topic);
            return;
        }
        turnRepository.save(ConversationTurn.createChildCompleted(
                turnId,
                event.conversationId(),
                turnSeq,
                text,
                payload.sttConfidence(),
                startedAt,
                completedAt,
                topic));
    }

    private static void validateExisting(
            ConversationTurn turn,
            String turnId,
            String conversationId,
            int turnSeq,
            String text,
            BigDecimal sttConfidence,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            ConversationTopic topic) {
        if (!turn.getTurnId().equals(turnId)
                || !turn.getConversationId().equals(conversationId)
                || turn.getTurnSeq() != turnSeq
                || turn.getSpeaker() != Speaker.CHILD
                || turn.getStatus() != TurnStatus.COMPLETED
                || !turn.getContent().equals(text)
                || !sameDecimal(turn.getSttConfidence(), sttConfidence)
                || !startedAt.equals(turn.getStartedAt())
                || !completedAt.equals(turn.getCompletedAt())
                || turn.getTopicType() != topic) {
            throw new ConversationEventContractException(
                    "turnId is already bound to a different turn.");
        }
    }

    private static ConversationTopic parseTopic(
            String value,
            ConversationTopic fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return ConversationTopic.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new ConversationEventContractException(
                    "payload.topicType is invalid.",
                    exception);
        }
    }

    private static void validateSttConfidence(BigDecimal confidence) {
        if (confidence != null
                && (confidence.compareTo(BigDecimal.ZERO) < 0
                || confidence.compareTo(BigDecimal.ONE) > 0)) {
            throw new ConversationEventContractException(
                    "payload.sttConfidence must be between 0 and 1.");
        }
    }

    private static boolean sameDecimal(BigDecimal first, BigDecimal second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.compareTo(second) == 0;
    }
}
