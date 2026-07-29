package com.A501.backend.domain.conversation.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.conversation.dto.stream.ConversationStreamDelivery;
import com.A501.backend.domain.conversation.dto.stream.ConversationStreamEvent;
import com.A501.backend.domain.conversation.dto.stream.ConversationStreamEventType;
import com.A501.backend.domain.conversation.entity.ConversationEvent;
import com.A501.backend.domain.conversation.entity.ConversationEventProcessingResult;
import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.repository.ConversationEventRepository;
import com.A501.backend.domain.conversation.repository.ConversationSessionRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class ConversationEventProcessor {

    private final ObjectMapper objectMapper;
    private final ConversationEventRepository eventRepository;
    private final ConversationSessionRepository sessionRepository;
    private final ConversationEventDispatcher eventDispatcher;
    private final Clock utcClock;

    public ConversationEventProcessor(
            ObjectMapper objectMapper,
            ConversationEventRepository eventRepository,
            ConversationSessionRepository sessionRepository,
            ConversationEventDispatcher eventDispatcher,
            @Qualifier("utcClock") Clock utcClock) {
        this.objectMapper = objectMapper;
        this.eventRepository = eventRepository;
        this.sessionRepository = sessionRepository;
        this.eventDispatcher = eventDispatcher;
        this.utcClock = utcClock;
    }

    @Transactional
    public ConversationEventProcessingOutcome process(ConversationStreamDelivery delivery) {
        if (eventRepository.existsByRedisRecordId(delivery.recordId())) {
            return ConversationEventProcessingOutcome.DUPLICATE;
        }

        ConversationStreamEvent event = deserialize(delivery.eventJson());
        validateEnvelope(event);
        if (eventRepository.existsById(event.eventId())) {
            return ConversationEventProcessingOutcome.DUPLICATE;
        }

        ConversationSession session = sessionRepository
                .findByIdForUpdate(event.conversationId())
                .orElseThrow(() -> new ConversationEventContractException(
                        "Conversation session does not exist."));
        // 종료된 세션에는 더 이상 이벤트를 반영하지 않는다. 특히 이탈·중단으로 원문을
        // 정리한 세션에 지연 도착한 확정 턴 이벤트가 턴을 재생성하지 못하게 한다.
        if (session.getStatus().isTerminal()) {
            record(delivery.recordId(), event, ConversationEventProcessingResult.IGNORED_STALE);
            return ConversationEventProcessingOutcome.IGNORED_STALE;
        }
        int currentEpoch = session.getCurrentSessionEpoch();
        if (event.sessionEpoch() < currentEpoch) {
            if (isConfirmedTurnBeforeCurrentEpoch(event, session)) {
                eventDispatcher.dispatch(event, session);
                record(delivery.recordId(), event, ConversationEventProcessingResult.APPLIED);
                return ConversationEventProcessingOutcome.APPLIED;
            }
            record(delivery.recordId(), event, ConversationEventProcessingResult.IGNORED_STALE);
            return ConversationEventProcessingOutcome.IGNORED_STALE;
        }
        if (event.sessionEpoch() > currentEpoch) {
            throw new ConversationEventContractException(
                    "Event sessionEpoch is ahead of the conversation session.");
        }

        eventDispatcher.dispatch(event, session);
        record(delivery.recordId(), event, ConversationEventProcessingResult.APPLIED);
        return ConversationEventProcessingOutcome.APPLIED;
    }

    private boolean isConfirmedTurnBeforeCurrentEpoch(
            ConversationStreamEvent event,
            ConversationSession session) {
        if (!isConfirmedTurnEvent(event.eventType())
                || session.getCurrentSessionEpochStartedAt() == null) {
            return false;
        }
        LocalDateTime occurredAt = event.occurredAt()
                .withOffsetSameInstant(ZoneOffset.UTC)
                .toLocalDateTime()
                .truncatedTo(ChronoUnit.MICROS);
        return !occurredAt.isAfter(session.getCurrentSessionEpochStartedAt());
    }

    private static boolean isConfirmedTurnEvent(ConversationStreamEventType eventType) {
        return eventType == ConversationStreamEventType.CHILD_TURN_FINALIZED
                || eventType == ConversationStreamEventType.AI_TURN_GENERATED
                || eventType == ConversationStreamEventType.AI_TURN_COMPLETED;
    }

    public ConversationStreamEvent deserialize(String eventJson) {
        try {
            return objectMapper.readValue(eventJson, ConversationStreamEvent.class);
        } catch (JacksonException exception) {
            throw new ConversationEventContractException(
                    "Conversation Event JSON is invalid.",
                    exception);
        }
    }

    private void validateEnvelope(ConversationStreamEvent event) {
        requireText(event.eventId(), "eventId");
        if (event.eventType() == null) {
            throw new ConversationEventContractException("eventType is required.");
        }
        requireText(event.conversationId(), "conversationId");
        requireText(event.realtimeSessionId(), "realtimeSessionId");
        if (event.sessionEpoch() < 1) {
            throw new ConversationEventContractException("sessionEpoch must be positive.");
        }
        if (event.occurredAt() == null) {
            throw new ConversationEventContractException("occurredAt is required.");
        }
    }

    private void record(
            String recordId,
            ConversationStreamEvent event,
            ConversationEventProcessingResult result) {
        LocalDateTime occurredAt = event.occurredAt()
                .withOffsetSameInstant(ZoneOffset.UTC)
                .toLocalDateTime()
                .truncatedTo(ChronoUnit.MICROS);
        LocalDateTime processedAt =
                LocalDateTime.now(utcClock).truncatedTo(ChronoUnit.MICROS);
        eventRepository.save(ConversationEvent.record(
                recordId,
                event,
                occurredAt,
                processedAt,
                result));
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ConversationEventContractException(fieldName + " is required.");
        }
    }
}
