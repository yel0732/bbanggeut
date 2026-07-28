package com.A501.backend.domain.conversation.service;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.A501.backend.domain.conversation.dto.stream.ConversationStreamDelivery;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "conversation.stream",
        name = "enabled",
        havingValue = "true")
public class ConversationEventConsumer {

    private final ConversationStreamClient streamClient;
    private final ConversationEventProcessor eventProcessor;
    private final ConversationStreamProperties properties;
    private final String consumerName = "conversation-" + UUID.randomUUID();

    public ConversationEventConsumer(
            ConversationStreamClient streamClient,
            ConversationEventProcessor eventProcessor,
            ConversationStreamProperties properties) {
        this.streamClient = streamClient;
        this.eventProcessor = eventProcessor;
        this.properties = properties;
    }

    @PostConstruct
    void initialize() {
        streamClient.ensureConsumerGroup();
    }

    @Scheduled(fixedDelayString = "${conversation.stream.poll-interval:PT1S}")
    public void consume() {
        for (ConversationStreamDelivery delivery :
                streamClient.readNew(consumerName, properties.batchSize())) {
            try {
                ConversationEventProcessingOutcome outcome =
                        eventProcessor.process(delivery);
                streamClient.acknowledge(delivery.recordId());
                log.debug(
                        "Conversation Event processed. recordId={}, outcome={}",
                        delivery.recordId(),
                        outcome);
            } catch (RuntimeException exception) {
                log.warn(
                        "Conversation Event delivery was not finalized and remains pending. recordId={}, cause={}",
                        delivery.recordId(),
                        exception.getClass().getSimpleName());
            }
        }
    }
}
