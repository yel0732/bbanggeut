package com.A501.backend.domain.conversation.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class RedisConversationStreamClientTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private StreamOperations<String, Object, Object> streamOperations;

    private RedisConversationStreamClient streamClient;

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
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        streamClient = new RedisConversationStreamClient(redisTemplate, properties);
    }

    @Test
    void createsConsumerGroupFromBeginning() {
        when(streamOperations.createGroup(
                        "conversation-events",
                        org.springframework.data.redis.connection.stream.ReadOffset.from("0-0"),
                        "spring-conversation-consumers"))
                .thenReturn("OK");

        streamClient.ensureConsumerGroup();

        verify(streamOperations).createGroup(
                "conversation-events",
                org.springframework.data.redis.connection.stream.ReadOffset.from("0-0"),
                "spring-conversation-consumers");
        verify(streamOperations, never()).add(any(MapRecord.class));
    }

    @Test
    void treatsExistingConsumerGroupAsSuccessfulInitialization() {
        when(streamOperations.createGroup(
                        "conversation-events",
                        org.springframework.data.redis.connection.stream.ReadOffset.from("0-0"),
                        "spring-conversation-consumers"))
                .thenThrow(new DataAccessResourceFailureException(
                        "BUSYGROUP Consumer Group name already exists"));

        assertThatCode(() -> streamClient.ensureConsumerGroup())
                .doesNotThrowAnyException();

        verify(streamOperations, never()).add(any(MapRecord.class));
    }

    @Test
    void bootstrapsMissingStreamThenDeletesBootstrapRecord() {
        when(streamOperations.createGroup(
                        "conversation-events",
                        org.springframework.data.redis.connection.stream.ReadOffset.from("0-0"),
                        "spring-conversation-consumers"))
                .thenThrow(new DataAccessResourceFailureException(
                        "ERR The XGROUP subcommand requires the key to exist"))
                .thenReturn("OK");
        RecordId bootstrapId = RecordId.of("1-0");
        when(streamOperations.add(any(MapRecord.class))).thenReturn(bootstrapId);

        streamClient.ensureConsumerGroup();

        verify(streamOperations).delete("conversation-events", bootstrapId);
    }
}
