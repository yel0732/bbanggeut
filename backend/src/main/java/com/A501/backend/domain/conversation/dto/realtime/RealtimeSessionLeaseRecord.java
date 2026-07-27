package com.A501.backend.domain.conversation.dto.realtime;

/**
 * {@code realtime:session:{conversationId}}에 저장되는 Session Lease 상태. Heartbeat
 * 갱신·만료 정리는 W02에서 다루며, 여기서는 발급 시점의 초기 {@code ACTIVE} 상태만 담당한다.
 */
public record RealtimeSessionLeaseRecord(
        String realtimeSessionId,
        int sessionEpoch,
        String state,
        long lastHeartbeatAt) {

    public static final String STATE_ACTIVE = "ACTIVE";

    public RealtimeSessionLeaseRecord {
        if (realtimeSessionId == null || realtimeSessionId.isBlank()) {
            throw new IllegalArgumentException("Realtime Session ID가 필요합니다.");
        }
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("Session Lease 상태가 필요합니다.");
        }
    }

    public static RealtimeSessionLeaseRecord active(
            String realtimeSessionId,
            int sessionEpoch,
            long lastHeartbeatAtEpochMillis) {
        return new RealtimeSessionLeaseRecord(
                realtimeSessionId, sessionEpoch, STATE_ACTIVE, lastHeartbeatAtEpochMillis);
    }
}
