package com.A501.backend.domain.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

class RealtimeSessionPropertiesTest {

    @Test
    void 정상_값을_허용한다() {
        RealtimeSessionProperties properties = new RealtimeSessionProperties(
                "ws://localhost:8000/realtime/signaling",
                null,
                Duration.ofSeconds(15));

        assertThat(properties.signalingUrl()).isEqualTo("ws://localhost:8000/realtime/signaling");
        assertThat(properties.iceServers()).isEmpty();
        assertThat(properties.leaseTtl()).isEqualTo(Duration.ofSeconds(15));
    }

    @Test
    void iceServers가_있으면_방어_복사한다() {
        List<String> mutable = new java.util.ArrayList<>(List.of("stun:example.com"));
        RealtimeSessionProperties properties = new RealtimeSessionProperties(
                "ws://localhost:8000/realtime/signaling", mutable, Duration.ofSeconds(15));

        mutable.clear();

        assertThat(properties.iceServers()).containsExactly("stun:example.com");
    }

    @Test
    void signalingUrl이_없으면_거부한다() {
        assertThatThrownBy(() -> new RealtimeSessionProperties(" ", null, Duration.ofSeconds(15)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void leaseTtl이_0이면_거부한다() {
        assertThatThrownBy(() -> new RealtimeSessionProperties(
                "ws://localhost:8000/realtime/signaling", null, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
