package com.A501.backend.domain.conversation.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.A501.backend.domain.conversation.dto.stream.ConversationStreamEvent;
import com.A501.backend.domain.conversation.dto.stream.ConversationStreamEventType;
import com.A501.backend.domain.conversation.entity.ConversationSession;

@Component
public class ConversationEventDispatcher {

    private final Map<ConversationStreamEventType, ConversationEventHandler> handlers;

    public ConversationEventDispatcher(List<ConversationEventHandler> handlers) {
        Map<ConversationStreamEventType, ConversationEventHandler> indexed =
                new EnumMap<>(ConversationStreamEventType.class);
        for (ConversationEventHandler handler : handlers) {
            for (ConversationStreamEventType eventType : handler.supportedTypes()) {
                if (indexed.put(eventType, handler) != null) {
                    throw new IllegalStateException(
                            "Multiple Conversation Event handlers registered for " + eventType);
                }
            }
        }
        this.handlers = Map.copyOf(indexed);
    }

    public void dispatch(ConversationStreamEvent event, ConversationSession session) {
        ConversationEventHandler handler = handlers.get(event.eventType());
        if (handler == null) {
            throw new ConversationEventContractException(
                    "Unsupported Conversation Event type: " + event.eventType());
        }
        handler.handle(event, session);
    }
}
