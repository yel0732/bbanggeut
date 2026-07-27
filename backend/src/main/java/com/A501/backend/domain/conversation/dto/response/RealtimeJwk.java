package com.A501.backend.domain.conversation.dto.response;

public record RealtimeJwk(
        String kty,
        String use,
        String alg,
        String kid,
        String n,
        String e) {
}
