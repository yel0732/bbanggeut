package com.A501.backend.domain.conversation.dto.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RealtimeTicketRecordTest {

    @Test
    void issued_상태로_생성한다() {
        RealtimeTicketRecord record = RealtimeTicketRecord.issued("conv_1", "rt_1", 1);

        assertThat(record.state()).isEqualTo(RealtimeTicketRecord.STATE_ISSUED);
        assertThat(record.conversationId()).isEqualTo("conv_1");
        assertThat(record.realtimeSessionId()).isEqualTo("rt_1");
        assertThat(record.sessionEpoch()).isEqualTo(1);
    }

    @Test
    void 대화_ID가_없으면_거부한다() {
        assertThatThrownBy(() -> new RealtimeTicketRecord(" ", "rt_1", 1, "ISSUED"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void Realtime_Session_ID가_없으면_거부한다() {
        assertThatThrownBy(() -> new RealtimeTicketRecord("conv_1", " ", 1, "ISSUED"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 상태가_없으면_거부한다() {
        assertThatThrownBy(() -> new RealtimeTicketRecord("conv_1", "rt_1", 1, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
