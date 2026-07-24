package com.A501.backend.domain.conversation.dto.response;

import com.A501.backend.domain.conversation.dto.internal.DialogueSlotsPayload;

public record ConversationHarnessStateResponse(
        ConversationSessionResponse session,
        DialogueSlotsPayload slots) {
}
