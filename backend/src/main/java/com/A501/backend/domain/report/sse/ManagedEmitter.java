package com.A501.backend.domain.report.sse;

import java.io.IOException;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SseEmitter를 감싸 부모 식별과 전송 직렬화를 담당한다.
 * 하트비트 스레드와 알림 전송 스레드가 같은 연결에 동시에 write하면
 * SSE 스트림이 깨질 수 있으므로 인스턴스 단위 lock으로 send를 직렬화한다.
 */
public class ManagedEmitter {

    private final Long parentId;
    private final SseEmitter emitter;
    private final Object sendLock = new Object();
    private volatile boolean closed;

    public ManagedEmitter(Long parentId, SseEmitter emitter) {
        this.parentId = parentId;
        this.emitter = emitter;
    }

    public Long parentId() {
        return parentId;
    }

    public SseEmitter emitter() {
        return emitter;
    }

    /**
     * 이벤트를 전송한다. 실패하면 연결을 닫힌 것으로 표시하고 false를 반환한다.
     * 호출자는 false일 때 레지스트리에서 이 emitter를 제거한다.
     */
    public boolean send(SseEmitter.SseEventBuilder event) {
        synchronized (sendLock) {
            if (closed) {
                return false;
            }
            try {
                emitter.send(event);
                return true;
            } catch (IOException | IllegalStateException exception) {
                closed = true;
                return false;
            }
        }
    }

    public boolean sendComment(String comment) {
        return send(SseEmitter.event().comment(comment));
    }

    public void markClosed() {
        this.closed = true;
    }
}
