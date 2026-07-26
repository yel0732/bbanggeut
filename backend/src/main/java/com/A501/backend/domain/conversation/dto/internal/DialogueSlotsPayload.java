package com.A501.backend.domain.conversation.dto.internal;

import java.util.List;

public record DialogueSlotsPayload(
        String event,
        List<String> people,
        String place,
        String emotion,
        String emotionReason,
        String memorableMoment) {

    public DialogueSlotsPayload {
        people = people == null ? List.of() : List.copyOf(people);
    }

    public static DialogueSlotsPayload empty() {
        return new DialogueSlotsPayload(null, List.of(), null, null, null, null);
    }
}
