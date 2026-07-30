package com.A501.backend.domain.report.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.A501.backend.domain.report.entity.ReportNotification;
import com.A501.backend.domain.report.entity.ReportNotificationType;
import com.A501.backend.domain.report.event.ReportNotificationCreatedEvent;
import com.A501.backend.domain.report.model.ReportNotificationSseProperties;
import com.A501.backend.domain.report.repository.ReportNotificationRepository;

class ReportNotificationStreamServiceTest {

    private static final LocalDate REPORT_DATE = LocalDate.of(2026, 7, 23);
    private static final String MESSAGE = "새로운 일일 리포트가 준비되었습니다.";

    private ReportNotificationEmitterRegistry registry;
    private ReportNotificationRepository notificationRepository;
    private Deque<SseEmitter> emitterQueue;
    private TestStreamService streamService;

    /** createEmitter를 오버라이드해 전송을 검증할 mock emitter를 주입한다. */
    private final class TestStreamService extends ReportNotificationStreamService {
        TestStreamService(
                ReportNotificationEmitterRegistry registry,
                ReportNotificationRepository notificationRepository,
                ReportNotificationSseProperties properties,
                Executor executor
        ) {
            super(registry, notificationRepository, properties, executor);
        }

        @Override
        protected SseEmitter createEmitter(long timeoutMillis) {
            return emitterQueue.poll();
        }
    }

    @BeforeEach
    void setUp() {
        registry = new ReportNotificationEmitterRegistry();
        notificationRepository = mock(ReportNotificationRepository.class);
        emitterQueue = new ArrayDeque<>();
        streamService = new TestStreamService(
                registry,
                notificationRepository,
                new ReportNotificationSseProperties(
                        Duration.ofMinutes(30),
                        Duration.ofSeconds(15)),
                Runnable::run); // 동기 실행으로 검증을 결정적으로 만든다.
    }

    private SseEmitter enqueueEmitter() {
        SseEmitter emitter = mock(SseEmitter.class);
        emitterQueue.add(emitter);
        return emitter;
    }

    @Test
    void subscribeWithoutLastEventIdRegistersAndSkipsReplay() {
        enqueueEmitter();

        streamService.subscribe(1L, null);

        assertThat(registry.findByParent(1L)).hasSize(1);
        verify(notificationRepository, never())
                .findByParentIdAndDeliveredAtIsNotNullAndIdGreaterThanOrderByIdAsc(any(), any());
    }

    @Test
    void subscribeWithLastEventIdReplaysMissedFromDatabase()
            throws IOException {
        SseEmitter emitter = enqueueEmitter();
        // 중첩 스터빙을 피하려고 알림 mock을 먼저 만들고 thenReturn에 넘긴다.
        ReportNotification first =
                notification(6L, ReportNotificationType.REPORT_COMPLETED);
        ReportNotification second =
                notification(7L, ReportNotificationType.REPORT_FAILED);
        when(notificationRepository
                .findByParentIdAndDeliveredAtIsNotNullAndIdGreaterThanOrderByIdAsc(1L, 5L))
                .thenReturn(List.of(first, second));

        streamService.subscribe(1L, 5L);

        verify(notificationRepository)
                .findByParentIdAndDeliveredAtIsNotNullAndIdGreaterThanOrderByIdAsc(1L, 5L);
        // connected comment 1건 + 복구 2건.
        verify(emitter, times(3)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void deliverSendsOnlyToTargetParent() throws IOException {
        SseEmitter targetEmitter = enqueueEmitter();
        streamService.subscribe(1L, null);
        SseEmitter otherEmitter = enqueueEmitter();
        streamService.subscribe(2L, null);

        streamService.deliver(new ReportNotificationCreatedEvent(
                10L,
                1L,
                20L,
                30L,
                ReportNotificationType.REPORT_COMPLETED,
                REPORT_DATE,
                MESSAGE));

        // target: connected(1) + deliver(1) = 2, other: connected(1)만.
        verify(targetEmitter, times(2))
                .send(any(SseEmitter.SseEventBuilder.class));
        verify(otherEmitter, times(1))
                .send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void deliverFailureRemovesOnlyFailedEmitter() throws IOException {
        enqueueEmitter();
        streamService.subscribe(1L, null);
        SseEmitter broken = enqueueEmitter();
        streamService.subscribe(1L, null);
        // broken은 연결 후 전송 시 끊긴 상태를 시뮬레이션한다.
        org.mockito.Mockito.doThrow(new IOException("broken pipe"))
                .when(broken).send(any(SseEmitter.SseEventBuilder.class));

        streamService.deliver(new ReportNotificationCreatedEvent(
                10L,
                1L,
                20L,
                30L,
                ReportNotificationType.REPORT_COMPLETED,
                REPORT_DATE,
                MESSAGE));

        assertThat(registry.findByParent(1L)).hasSize(1);
    }

    @Test
    void heartbeatRemovesDeadConnections() throws IOException {
        enqueueEmitter();
        streamService.subscribe(1L, null);
        SseEmitter dead = enqueueEmitter();
        streamService.subscribe(2L, null);
        org.mockito.Mockito.doThrow(new IOException("closed"))
                .when(dead).send(any(SseEmitter.SseEventBuilder.class));

        streamService.sendHeartbeat();

        assertThat(registry.findByParent(1L)).hasSize(1);
        assertThat(registry.findByParent(2L)).isEmpty();
    }

    private ReportNotification notification(
            Long id,
            ReportNotificationType type
    ) {
        ReportNotification notification = mock(ReportNotification.class);
        when(notification.getId()).thenReturn(id);
        when(notification.getNotificationType()).thenReturn(type);
        when(notification.getReportDate()).thenReturn(REPORT_DATE);
        when(notification.getMessage()).thenReturn(MESSAGE);
        return notification;
    }
}
