package com.A501.backend.domain.conversation.dto.stream;

import java.time.OffsetDateTime;

public record AiTurnCompletedPayload(
        String text,
        OffsetDateTime audioStartedAt,
        OffsetDateTime audioCompletedAt) {
}
