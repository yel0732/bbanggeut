package com.A501.backend.domain.conversation.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.conversation.dto.stream.ConversationStreamDelivery;
import com.A501.backend.domain.conversation.dto.stream.ConversationStreamEvent;
import com.A501.backend.domain.conversation.entity.ConversationEventFailure;
import com.A501.backend.domain.conversation.repository.ConversationEventFailureRepository;

@Service
public class ConversationEventFailureService {

    private final ConversationEventFailureRepository failureRepository;
    private final ConversationEventProcessor eventProcessor;
    private final Clock utcClock;

    public ConversationEventFailureService(
            ConversationEventFailureRepository failureRepository,
            ConversationEventProcessor eventProcessor,
            @Qualifier("utcClock") Clock utcClock) {
        this.failureRepository = failureRepository;
        this.eventProcessor = eventProcessor;
        this.utcClock = utcClock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(
            ConversationStreamDelivery delivery,
            RuntimeException failure) {
        if (failureRepository.existsByRedisRecordId(delivery.recordId())) {
            return;
        }

        ConversationStreamEvent event = deserializeOrNull(delivery.eventJson());
        failureRepository.save(ConversationEventFailure.record(
                delivery.recordId(),
                event == null ? null : event.eventId(),
                event == null ? null : event.conversationId(),
                event == null ? null : event.eventType(),
                delivery.deliveryCount(),
                LocalDateTime.now(utcClock).truncatedTo(ChronoUnit.MICROS),
                "Processing failed: " + failure.getClass().getSimpleName()));
    }

    private ConversationStreamEvent deserializeOrNull(String eventJson) {
        try {
            return eventProcessor.deserialize(eventJson);
        } catch (ConversationEventContractException exception) {
            return null;
        }
    }
}
