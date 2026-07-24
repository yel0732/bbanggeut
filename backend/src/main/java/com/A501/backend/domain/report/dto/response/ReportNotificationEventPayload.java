package com.A501.backend.domain.report.dto.response;

import java.time.LocalDate;

/**
 * SSE data 필드로 나가는 알림 본문. 게임 결과·점수·대화 내용·내부 실패 원인은
 * 담지 않는다.
 */
public record ReportNotificationEventPayload(
        LocalDate reportDate,
        String message
) {
}
