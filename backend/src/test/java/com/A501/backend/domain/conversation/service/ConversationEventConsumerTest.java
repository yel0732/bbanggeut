package com.A501.backend.domain.conversation.service;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.A501.backend.domain.conversation.dto.stream.ConversationStreamDelivery;

@ExtendWith(MockitoExtension.class)
class ConversationEventConsumerTest {

    @Mock
    private ConversationStreamClient streamClient;

    @Mock
    private ConversationEventProcessor eventProcessor;

    private ConversationEventConsumer consumer;

    @BeforeEach
    void setUp() {
        ConversationStreamProperties properties = new ConversationStreamProperties(
                "conversation-events",
                "spring-conversation-consumers",
                true,
                Duration.ofSeconds(1),
                20,
                new ConversationStreamProperties.Recovery(
                        true,
                        Duration.ofSeconds(30),
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(30),
                        3,
                        100));
        consumer = new ConversationEventConsumer(streamClient, eventProcessor, properties);
    }

    @Test
    void acknowledgesOnlyAfterTransactionProcessorReturns() {
        ConversationStreamDelivery delivery =
                new ConversationStreamDelivery("1000-0", "{}", 1);
        when(streamClient.readNew(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(20)))
                .thenReturn(List.of(delivery));
        when(eventProcessor.process(delivery))
                .thenReturn(ConversationEventProcessingOutcome.APPLIED);

        consumer.consume();

        InOrder order = inOrder(eventProcessor, streamClient);
        order.verify(eventProcessor).process(delivery);
        order.verify(streamClient).acknowledge("1000-0");
    }

    @Test
    void leavesMessagePendingWhenTransactionProcessorFails() {
        ConversationStreamDelivery delivery =
                new ConversationStreamDelivery("1000-1", "{}", 1);
        when(streamClient.readNew(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(20)))
                .thenReturn(List.of(delivery));
        when(eventProcessor.process(delivery))
                .thenThrow(new ConversationEventContractException("invalid"));

        consumer.consume();

        verify(streamClient, never()).acknowledge("1000-1");
    }
}
