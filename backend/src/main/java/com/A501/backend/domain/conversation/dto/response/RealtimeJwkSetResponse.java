package com.A501.backend.domain.conversation.dto.response;

import java.util.List;

public record RealtimeJwkSetResponse(List<RealtimeJwk> keys) {

    public RealtimeJwkSetResponse {
        keys = keys == null ? List.of() : List.copyOf(keys);
    }
}
