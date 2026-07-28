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
        if (!properties.recovery().enabled()) {
            // Consumer는 새 메시지만 읽는다(ReadOffset.lastConsumed). process() 실패로
            // ACK되지 않은 메시지는 Recovery Scheduler만 재처리·dead-letter한다 —
            // Recovery가 꺼져 있으면 그 메시지는 PEL에 영원히 남아 확정 턴이 유실된다.
            // 여러 노드 중 전용 Recovery 노드만 켜는 구성은 유효하므로 실패시키지 않고
            // 눈에 띄게 경고만 한다(DIARY-3-W06 리뷰 finding ②).
            log.warn(
                    "Conversation Stream consumer is enabled but recovery is disabled. "
                            + "Unacknowledged messages will not be retried or dead-lettered "
                            + "unless another node runs recovery. "
                            + "Set conversation.stream.recovery.enabled=true on at least one node.");
        }
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
