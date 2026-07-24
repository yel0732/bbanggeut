package com.A501.backend.domain.report.sse;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 주기적으로 하트비트를 보내 끊긴 SSE 연결을 감지하고 정리한다.
 * 주기는 report.notification.sse.heartbeat-interval 설정으로 관리한다.
 */
@Component
@RequiredArgsConstructor
public class SseHeartbeatScheduler {

    private final ReportNotificationStreamService streamService;

    @Scheduled(fixedRateString =
            "${report.notification.sse.heartbeat-interval}")
    public void heartbeat() {
        streamService.sendHeartbeat();
    }
}
