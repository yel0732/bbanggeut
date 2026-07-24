package com.A501.backend.domain.report.event;

import java.time.LocalDate;

import com.A501.backend.domain.report.entity.ReportNotificationType;

/**
 * 알림이 DB에 저장되고 트랜잭션이 커밋된 뒤 SSE 전송을 트리거하기 위한 이벤트.
 * SSE 전송이 저장 트랜잭션 내부에 끼어들지 않도록 커밋 이후 전달 통로로만 쓴다.
 */
public record ReportNotificationCreatedEvent(
        Long notificationId,
        Long parentId,
        ReportNotificationType notificationType,
        LocalDate reportDate,
        String message
) {
}
