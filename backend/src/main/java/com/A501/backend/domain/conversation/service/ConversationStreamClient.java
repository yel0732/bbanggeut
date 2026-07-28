package com.A501.backend.domain.conversation.service;

import java.time.Duration;
import java.util.List;

import com.A501.backend.domain.conversation.dto.stream.ConversationStreamDelivery;
import com.A501.backend.domain.conversation.dto.stream.ConversationStreamPendingMessage;

public interface ConversationStreamClient {

    void ensureConsumerGroup();

    List<ConversationStreamDelivery> readNew(String consumerName, int count);

    void acknowledge(String recordId);

    List<ConversationStreamPendingMessage> findPending(int count, Duration minIdle);

    List<ConversationStreamDelivery> claim(
            String consumerName,
            List<ConversationStreamPendingMessage> pendingMessages,
            Duration minIdle);
}
