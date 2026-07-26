package com.A501.backend.domain.conversation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTextTurnRequest(
        @NotBlank
        @Size(max = 2000)
        String text) {

    public CreateTextTurnRequest {
        text = text == null ? null : text.trim();
    }
}
