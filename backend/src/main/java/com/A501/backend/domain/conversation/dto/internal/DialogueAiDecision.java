package com.A501.backend.domain.conversation.dto.internal;

public enum DialogueAiDecision {
    CONTINUE_TOPIC,
    SWITCH_TOPIC,
    TERMINATE_SESSION;

    @Deprecated
    public static final DialogueAiDecision CONTINUE = CONTINUE_TOPIC;

    @Deprecated
    public static final DialogueAiDecision TERMINATE = TERMINATE_SESSION;
}
