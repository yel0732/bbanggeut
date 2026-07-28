package com.A501.backend.domain.conversation.service;

import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.A501.backend.domain.conversation.dto.stream.ConversationStreamDelivery;
import com.A501.backend.domain.conversation.dto.stream.ConversationStreamPendingMessage;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "conversation.stream.recovery",
        name = "enabled",
        havingValue = "true")
public class ConversationEventRecoveryScheduler {

    private final ConversationStreamClient streamClient;
    private final ConversationEventProcessor eventProcessor;
    private final ConversationEventFailureService failureService;
    private final ConversationStreamProperties properties;
    private final String consumerName = "conversation-recovery-" + UUID.randomUUID();

    public ConversationEventRecoveryScheduler(
            ConversationStreamClient streamClient,
            ConversationEventProcessor eventProcessor,
            ConversationEventFailureService failureService,
            ConversationStreamProperties properties) {
        this.streamClient = streamClient;
        this.eventProcessor = eventProcessor;
        this.failureService = failureService;
        this.properties = properties;
    }

    @Scheduled(
            fixedDelayString = "${conversation.stream.recovery.interval:PT30S}",
            initialDelayString = "${conversation.stream.recovery.initial-delay:PT10S}")
    public void recover() {
        ConversationStreamProperties.Recovery recovery = properties.recovery();
        List<ConversationStreamPendingMessage> pending = streamClient.findPending(
                recovery.batchSize(),
                recovery.minIdle());
        List<ConversationStreamDelivery> claimed = streamClient.claim(
                consumerName,
                pending,
                recovery.minIdle());
        for (ConversationStreamDelivery delivery : claimed) {
            recover(delivery, recovery.maxAttempts());
        }
    }

    private void recover(
            ConversationStreamDelivery delivery,
            int maxAttempts) {
        ConversationEventProcessingOutcome outcome;
        try {
            outcome = eventProcessor.process(delivery);
        } catch (RuntimeException exception) {
            if (delivery.deliveryCount() < maxAttempts) {
                log.warn(
                        "Pending Conversation Event retry failed. recordId={}, deliveryCount={}, cause={}",
                        delivery.recordId(),
                        delivery.deliveryCount(),
                        exception.getClass().getSimpleName());
                return;
            }

            failureService.recordFailure(delivery, exception);
            streamClient.acknowledge(delivery.recordId());
            log.error(
                    "Conversation Event isolated after retry exhaustion. recordId={}, deliveryCount={}, cause={}",
                    delivery.recordId(),
                    delivery.deliveryCount(),
                    exception.getClass().getSimpleName());
            return;
        }

        streamClient.acknowledge(delivery.recordId());
        log.info(
                "Pending Conversation Event recovered. recordId={}, outcome={}, deliveryCount={}",
                delivery.recordId(),
                outcome,
                delivery.deliveryCount());
    }
}
