package com.A501.backend.domain.conversation.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.A501.backend.domain.conversation.dto.stream.ConversationStreamEvent;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class ConversationEventPayloadReader {

    private final ObjectMapper objectMapper;

    public <T> T read(ConversationStreamEvent event, Class<T> payloadType) {
        if (event.payload() == null || event.payload().isNull()) {
            throw new ConversationEventContractException(
                    event.eventType() + " payload is required.");
        }
        try {
            return objectMapper.treeToValue(event.payload(), payloadType);
        } catch (JacksonException exception) {
            throw new ConversationEventContractException(
                    event.eventType() + " payload is invalid.",
                    exception);
        }
    }

    public String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ConversationEventContractException(fieldName + " is required.");
        }
        return value;
    }

    public int requireTurnSeq(Integer turnSeq) {
        if (turnSeq == null || turnSeq < 1) {
            throw new ConversationEventContractException("turnSeq must be positive.");
        }
        return turnSeq;
    }

    public LocalDateTime toUtc(OffsetDateTime value, String fieldName) {
        if (value == null) {
            throw new ConversationEventContractException(fieldName + " is required.");
        }
        return value.withOffsetSameInstant(ZoneOffset.UTC)
                .toLocalDateTime()
                .truncatedTo(ChronoUnit.MICROS);
    }
}
