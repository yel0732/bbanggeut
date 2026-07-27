package com.A501.backend.domain.conversation.dto.realtime;

import java.util.List;

public record RealtimeChildContext(
        String displayName,
        String ageBand,
        List<String> interests) {

    public RealtimeChildContext {
        interests = interests == null ? List.of() : List.copyOf(interests);
    }
}
