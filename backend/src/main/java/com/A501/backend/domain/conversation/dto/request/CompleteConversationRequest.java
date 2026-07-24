package com.A501.backend.domain.conversation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record CompleteConversationRequest(
        @Schema(
                description = "대화 세션 종료 사유",
                allowableValues = {
                        "USER_FINISHED",
                        "MAX_QUESTION_REACHED",
                        "CHILD_REQUESTED_STOP",
                        "REQUIRED_SLOTS_FILLED",
                        "INACTIVITY_TIMEOUT",
                        "CONNECTION_LOST",
                        "PAGE_CLOSED",
                        "SYSTEM_ERROR"
                })
        String reason) {
}
