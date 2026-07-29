package com.A501.backend.domain.conversation.service;

import java.util.Set;

import com.A501.backend.domain.conversation.dto.stream.ConversationStreamEvent;
import com.A501.backend.domain.conversation.dto.stream.ConversationStreamEventType;
import com.A501.backend.domain.conversation.entity.ConversationSession;

public interface ConversationEventHandler {

    Set<ConversationStreamEventType> supportedTypes();

    void handle(ConversationStreamEvent event, ConversationSession session);
}
