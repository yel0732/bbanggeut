package com.A501.backend.domain.conversation.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.A501.backend.domain.conversation.dto.stream.ConversationStreamDelivery;
import com.A501.backend.domain.conversation.dto.stream.ConversationStreamPendingMessage;

@Component
@ConditionalOnProperty(
        prefix = "conversation.stream",
        name = "enabled",
        havingValue = "true")
public class RedisConversationStreamClient implements ConversationStreamClient {

    private static final String EVENT_FIELD = "event";
    private static final String BOOTSTRAP_FIELD = "_bootstrap";

    private final StringRedisTemplate redisTemplate;
    private final ConversationStreamProperties properties;

    public RedisConversationStreamClient(
            StringRedisTemplate redisTemplate,
            ConversationStreamProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public void ensureConsumerGroup() {
        StreamOperations<String, Object, Object> operations = redisTemplate.opsForStream();
        try {
            operations.createGroup(
                    properties.key(),
                    ReadOffset.from("0-0"),
                    properties.group());
        } catch (DataAccessException exception) {
            if (containsMessage(exception, "BUSYGROUP")) {
                return;
            }
            if (!containsMessage(exception, "requires the key to exist")) {
                throw exception;
            }

            RecordId bootstrapId = operations.add(
                    StreamRecords.mapBacked(Map.of(BOOTSTRAP_FIELD, "true"))
                            .withStreamKey(properties.key()));
            try {
                operations.createGroup(
                        properties.key(),
                        ReadOffset.from("0-0"),
                        properties.group());
            } finally {
                if (bootstrapId != null) {
                    operations.delete(properties.key(), bootstrapId);
                }
            }
        }
    }

    @Override
    public List<ConversationStreamDelivery> readNew(String consumerName, int count) {
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                Consumer.from(properties.group(), consumerName),
                StreamReadOptions.empty().count(count),
                StreamOffset.create(properties.key(), ReadOffset.lastConsumed()));
        return toDeliveries(records, ignored -> 1L);
    }

    @Override
    public void acknowledge(String recordId) {
        redisTemplate.opsForStream().acknowledge(
                properties.key(),
                properties.group(),
                RecordId.of(recordId));
    }

    @Override
    public List<ConversationStreamPendingMessage> findPending(int count, Duration minIdle) {
        return redisTemplate.opsForStream()
                .pending(
                        properties.key(),
                        properties.group(),
                        Range.unbounded(),
                        count,
                        minIdle)
                .stream()
                .map(message -> new ConversationStreamPendingMessage(
                        message.getIdAsString(),
                        message.getTotalDeliveryCount()))
                .toList();
    }

    @Override
    public List<ConversationStreamDelivery> claim(
            String consumerName,
            List<ConversationStreamPendingMessage> pendingMessages,
            Duration minIdle) {
        if (pendingMessages.isEmpty()) {
            return List.of();
        }

        Map<String, Long> deliveryCounts = pendingMessages.stream()
                .collect(Collectors.toMap(
                        ConversationStreamPendingMessage::recordId,
                        message -> message.deliveryCount() + 1));
        RecordId[] recordIds = pendingMessages.stream()
                .map(ConversationStreamPendingMessage::recordId)
                .map(RecordId::of)
                .toArray(RecordId[]::new);
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().claim(
                properties.key(),
                properties.group(),
                consumerName,
                minIdle,
                recordIds);
        return toDeliveries(
                records,
                recordId -> deliveryCounts.getOrDefault(recordId, 1L));
    }

    private static List<ConversationStreamDelivery> toDeliveries(
            List<MapRecord<String, Object, Object>> records,
            Function<String, Long> deliveryCount) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        return records.stream()
                .map(record -> {
                    Object event = record.getValue().get(EVENT_FIELD);
                    String recordId = record.getId().getValue();
                    return new ConversationStreamDelivery(
                            recordId,
                            event == null ? "{}" : event.toString(),
                            deliveryCount.apply(recordId));
                })
                .toList();
    }

    private static boolean containsMessage(Throwable throwable, String expected) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(expected)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
