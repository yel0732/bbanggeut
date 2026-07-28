package com.A501.backend.domain.conversation.service;

public enum ConversationEventProcessingOutcome {
    APPLIED,
    DUPLICATE,
    IGNORED_STALE
}
