package com.A501.backend.domain.conversation.dto.stream;

import java.time.OffsetDateTime;

public record AiTurnInterruptedPayload(
        OffsetDateTime interruptedAt,
        String reason,
        String interruptionId) {
}
