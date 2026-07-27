package com.A501.backend.domain.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class RealtimeContextPropertiesTest {

    @Test
    void 양수_TTL을_허용한다() {
        RealtimeContextProperties properties = new RealtimeContextProperties(Duration.ofMinutes(30));

        assertThat(properties.ttl()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void TTL이_null이면_거부한다() {
        assertThatThrownBy(() -> new RealtimeContextProperties(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void TTL이_0이면_거부한다() {
        assertThatThrownBy(() -> new RealtimeContextProperties(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void TTL이_음수면_거부한다() {
        assertThatThrownBy(() -> new RealtimeContextProperties(Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
