package com.A501.backend.domain.conversation.dto.stream;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ChildTurnFinalizedPayload(
        String text,
        BigDecimal sttConfidence,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        String topicType) {
}
