package com.A501.backend.domain.report.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class ManagedEmitterTest {

    @Test
    void sendReturnsTrueWhenEmitterAccepts() throws IOException {
        SseEmitter emitter = mock(SseEmitter.class);
        doNothing().when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        ManagedEmitter managed = new ManagedEmitter(1L, emitter);

        boolean sent = managed.send(SseEmitter.event().data("x"));

        assertThat(sent).isTrue();
    }

    @Test
    void sendFailureMarksClosedAndSkipsFurtherWrites() throws IOException {
        SseEmitter emitter = mock(SseEmitter.class);
        doThrow(new IOException("broken pipe"))
                .when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        ManagedEmitter managed = new ManagedEmitter(1L, emitter);

        boolean firstSend = managed.send(SseEmitter.event().data("x"));
        boolean secondSend = managed.send(SseEmitter.event().data("y"));

        assertThat(firstSend).isFalse();
        assertThat(secondSend).isFalse();
        // 첫 실패 이후 닫힘으로 표시되어 emitter를 다시 건드리지 않는다.
        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void sendAfterMarkClosedIsNoOp() throws IOException {
        SseEmitter emitter = mock(SseEmitter.class);
        ManagedEmitter managed = new ManagedEmitter(1L, emitter);
        managed.markClosed();

        boolean sent = managed.send(SseEmitter.event().data("x"));

        assertThat(sent).isFalse();
        verify(emitter, times(0)).send(any(SseEmitter.SseEventBuilder.class));
    }
}
