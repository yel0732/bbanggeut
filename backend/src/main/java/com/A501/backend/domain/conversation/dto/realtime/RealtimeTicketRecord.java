package com.A501.backend.domain.conversation.dto.realtime;

/**
 * {@code realtime:ticket:{jti}}에 저장되는 Ticket 상태. {@code ISSUED}는 W01(발급)에서,
 * {@code CONSUMED} 전이는 W02(원자적 소비)에서 다룬다.
 */
public record RealtimeTicketRecord(
        String conversationId,
        String realtimeSessionId,
        int sessionEpoch,
        String state) {

    public static final String STATE_ISSUED = "ISSUED";

    public RealtimeTicketRecord {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("대화 ID가 필요합니다.");
        }
        if (realtimeSessionId == null || realtimeSessionId.isBlank()) {
            throw new IllegalArgumentException("Realtime Session ID가 필요합니다.");
        }
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("Ticket 상태가 필요합니다.");
        }
    }

    public static RealtimeTicketRecord issued(
            String conversationId,
            String realtimeSessionId,
            int sessionEpoch) {
        return new RealtimeTicketRecord(
                conversationId, realtimeSessionId, sessionEpoch, STATE_ISSUED);
    }
}
