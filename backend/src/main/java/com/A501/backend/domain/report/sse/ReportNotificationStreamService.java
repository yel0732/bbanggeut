package com.A501.backend.domain.report.sse;

import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.A501.backend.domain.report.dto.response.ReportNotificationEventPayload;
import com.A501.backend.domain.report.entity.ReportNotification;
import com.A501.backend.domain.report.entity.ReportNotificationType;
import com.A501.backend.domain.report.event.ReportNotificationCreatedEvent;
import com.A501.backend.domain.report.model.ReportNotificationSseProperties;
import com.A501.backend.domain.report.repository.ReportNotificationRepository;

/**
 * SSE 연결 수립·복구·전송·하트비트를 담당하는 전달 통로.
 * 알림 저장소가 아니라 이미 저장된 알림을 접속 중인 부모에게 밀어주는 역할만 한다.
 * 전송 실패는 해당 연결만 정리하고 저장된 알림이나 리포트 상태를 되돌리지 않는다.
 */
@Service
public class ReportNotificationStreamService {

    private static final String CONNECTED_COMMENT = "connected";
    private static final String HEARTBEAT_COMMENT = "heartbeat";

    private final ReportNotificationEmitterRegistry registry;
    private final ReportNotificationRepository notificationRepository;
    private final ReportNotificationSseProperties properties;
    private final Executor executor;

    public ReportNotificationStreamService(
            ReportNotificationEmitterRegistry registry,
            ReportNotificationRepository notificationRepository,
            ReportNotificationSseProperties properties,
            @Qualifier("sseDeliveryExecutor") Executor executor
    ) {
        this.registry = registry;
        this.notificationRepository = notificationRepository;
        this.properties = properties;
        this.executor = executor;
    }

    /**
     * 새 SSE 연결을 만들고 레지스트리에 먼저 등록한 뒤,
     * Last-Event-ID가 있으면 그 이후의 알림을 DB에서 재조회해 재전송한다.
     * 라이브 이벤트와 복구가 겹치면 클라이언트가 이벤트 id로 중복을 제거한다.
     */
    public SseEmitter subscribe(Long parentId, Long lastEventId) {
        SseEmitter emitter = createEmitter(properties.timeout().toMillis());
        ManagedEmitter managed = new ManagedEmitter(parentId, emitter);
        registerCallbacks(emitter, managed);
        registry.add(managed);
        managed.sendComment(CONNECTED_COMMENT);
        replayMissed(managed, parentId, lastEventId);
        return emitter;
    }

    /**
     * 커밋된 알림을 접속 중인 해당 부모의 모든 연결로 전송한다.
     * 전송은 전용 executor에서 비동기로 처리해 호출 스레드를 막지 않는다.
     */
    public void deliver(ReportNotificationCreatedEvent event) {
        ReportNotificationEventPayload payload =
                new ReportNotificationEventPayload(
                        event.notificationId(),
                        event.reportId(),
                        event.childId(),
                        event.reportDate(),
                        event.message());
        for (ManagedEmitter managed : registry.findByParent(event.parentId())) {
            dispatchSend(
                    managed,
                    event.notificationId(),
                    event.notificationType(),
                    payload);
        }
    }

    /**
     * 모든 연결에 하트비트 comment를 보내 끊긴 연결을 감지하고 정리한다.
     */
    public void sendHeartbeat() {
        for (ManagedEmitter managed : registry.findAll()) {
            executor.execute(() -> {
                if (!managed.sendComment(HEARTBEAT_COMMENT)) {
                    registry.remove(managed);
                }
            });
        }
    }

    // 테스트에서 emitter 전송을 검증할 수 있도록 생성 지점을 분리한다.
    protected SseEmitter createEmitter(long timeoutMillis) {
        return new SseEmitter(timeoutMillis);
    }

    private void registerCallbacks(SseEmitter emitter, ManagedEmitter managed) {
        emitter.onCompletion(() -> {
            managed.markClosed();
            registry.remove(managed);
        });
        emitter.onTimeout(() -> {
            managed.markClosed();
            registry.remove(managed);
            emitter.complete();
        });
        emitter.onError(throwable -> {
            managed.markClosed();
            registry.remove(managed);
        });
    }

    private void replayMissed(
            ManagedEmitter managed,
            Long parentId,
            Long lastEventId
    ) {
        if (lastEventId == null) {
            return;
        }
        for (ReportNotification notification : notificationRepository
                .findByParentIdAndDeliveredAtIsNotNullAndIdGreaterThanOrderByIdAsc(
                        parentId,
                        lastEventId)) {
            boolean sent = managed.send(toEvent(
                    notification.getId(),
                    notification.getNotificationType(),
                    new ReportNotificationEventPayload(
                            notification.getId(),
                            notification.getReportId(),
                            notification.getChildId(),
                            notification.getReportDate(),
                            notification.getMessage())));
            if (!sent) {
                registry.remove(managed);
                return;
            }
        }
    }

    private void dispatchSend(
            ManagedEmitter managed,
            Long notificationId,
            ReportNotificationType notificationType,
            ReportNotificationEventPayload payload
    ) {
        executor.execute(() -> {
            if (!managed.send(
                    toEvent(notificationId, notificationType, payload))) {
                registry.remove(managed);
            }
        });
    }

    private SseEmitter.SseEventBuilder toEvent(
            Long notificationId,
            ReportNotificationType notificationType,
            ReportNotificationEventPayload payload
    ) {
        return SseEmitter.event()
                .id(String.valueOf(notificationId))
                .name(notificationType.name())
                .data(payload, MediaType.APPLICATION_JSON);
    }
}
