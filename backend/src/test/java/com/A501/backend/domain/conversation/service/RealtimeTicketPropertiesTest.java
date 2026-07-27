package com.A501.backend.domain.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class RealtimeTicketPropertiesTest {

    @Test
    void PEM_없이도_생성할_수_있다() {
        RealtimeTicketProperties properties = new RealtimeTicketProperties(
                "spring-backend", "ai-realtime", Duration.ofMinutes(5), null, null);

        assertThat(properties.privateKeyPem()).isNull();
        assertThat(properties.publicKeyPem()).isNull();
    }

    @Test
    void issuer가_없으면_거부한다() {
        assertThatThrownBy(() -> new RealtimeTicketProperties(
                " ", "ai-realtime", Duration.ofMinutes(5), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void audience가_없으면_거부한다() {
        assertThatThrownBy(() -> new RealtimeTicketProperties(
                "spring-backend", " ", Duration.ofMinutes(5), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void TTL이_음수면_거부한다() {
        assertThatThrownBy(() -> new RealtimeTicketProperties(
                "spring-backend", "ai-realtime", Duration.ofMinutes(-1), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
