package com.A501.backend.domain.conversation.service;

public class ConversationEventContractException extends RuntimeException {

    public ConversationEventContractException(String message) {
        super(message);
    }

    public ConversationEventContractException(String message, Throwable cause) {
        super(message, cause);
    }
}
