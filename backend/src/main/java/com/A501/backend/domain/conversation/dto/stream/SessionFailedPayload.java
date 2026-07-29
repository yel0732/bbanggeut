package com.A501.backend.domain.conversation.dto.stream;

import java.time.OffsetDateTime;

public record SessionFailedPayload(
        String code,
        String message,
        OffsetDateTime failedAt) {
}
