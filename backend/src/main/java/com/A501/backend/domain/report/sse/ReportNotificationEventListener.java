package com.A501.backend.domain.report.sse;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.A501.backend.domain.report.event.ReportNotificationCreatedEvent;

import lombok.RequiredArgsConstructor;

/**
 * 알림 저장 트랜잭션이 커밋된 뒤에만 SSE 전송을 트리거한다.
 * 실제 전송은 StreamService가 전용 executor로 비동기 처리하므로
 * 이 리스너는 커밋 스레드를 오래 붙잡지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ReportNotificationEventListener {

    private final ReportNotificationStreamService streamService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReportNotificationCreated(
            ReportNotificationCreatedEvent event
    ) {
        streamService.deliver(event);
    }
}
