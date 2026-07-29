package com.A501.backend.domain.conversation.dto.stream;

import java.time.OffsetDateTime;

public record SessionCompletedPayload(
        String reason,
        OffsetDateTime completedAt) {
}
