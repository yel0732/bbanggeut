package com.A501.backend.domain.conversation.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record RealtimeSessionResponse(
        String conversationId,
        String realtimeSessionId,
        int sessionEpoch,
        String signalingUrl,
        String realtimeTicket,
        OffsetDateTime expiresAt,
        List<String> iceServers) {

    public RealtimeSessionResponse {
        iceServers = iceServers == null ? List.of() : List.copyOf(iceServers);
    }

    public static RealtimeSessionResponse of(
            String conversationId,
            String realtimeSessionId,
            int sessionEpoch,
            String signalingUrl,
            String realtimeTicket,
            OffsetDateTime expiresAt,
            List<String> iceServers) {
        return new RealtimeSessionResponse(
                conversationId,
                realtimeSessionId,
                sessionEpoch,
                signalingUrl,
                realtimeTicket,
                expiresAt,
                iceServers);
    }
}
