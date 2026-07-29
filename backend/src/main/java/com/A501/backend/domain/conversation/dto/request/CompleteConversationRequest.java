package com.A501.backend.domain.conversation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record CompleteConversationRequest(
        @Schema(
                description = "대화 세션 종료 사유. 이 API로 종료한 대화는 완주로 보지 않아"
                        + " 대화 내용이 정리되며, 완주 사유(REQUIRED_SLOTS_FILLED,"
                        + " MAX_QUESTION_REACHED)는 대화 엔진만 사용할 수 있다.",
                allowableValues = {
                        "USER_FINISHED",
                        "USER_ABANDONED",
                        "CHILD_REQUESTED_STOP",
                        "INACTIVITY_TIMEOUT",
                        "CONNECTION_LOST",
                        "PAGE_CLOSED",
                        "SYSTEM_ERROR"
                })
        String reason) {
}
