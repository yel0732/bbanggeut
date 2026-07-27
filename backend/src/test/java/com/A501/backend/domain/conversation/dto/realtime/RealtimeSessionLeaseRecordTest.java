package com.A501.backend.domain.conversation.dto.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RealtimeSessionLeaseRecordTest {

    @Test
    void active_상태로_생성한다() {
        RealtimeSessionLeaseRecord record = RealtimeSessionLeaseRecord.active("rt_1", 1, 1_000L);

        assertThat(record.state()).isEqualTo(RealtimeSessionLeaseRecord.STATE_ACTIVE);
        assertThat(record.realtimeSessionId()).isEqualTo("rt_1");
        assertThat(record.sessionEpoch()).isEqualTo(1);
        assertThat(record.lastHeartbeatAt()).isEqualTo(1_000L);
    }

    @Test
    void Realtime_Session_ID가_없으면_거부한다() {
        assertThatThrownBy(() -> new RealtimeSessionLeaseRecord(" ", 1, "ACTIVE", 1_000L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 상태가_없으면_거부한다() {
        assertThatThrownBy(() -> new RealtimeSessionLeaseRecord("rt_1", 1, " ", 1_000L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
