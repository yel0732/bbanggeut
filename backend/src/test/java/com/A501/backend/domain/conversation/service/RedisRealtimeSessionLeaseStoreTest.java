package com.A501.backend.domain.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.A501.backend.domain.conversation.dto.realtime.RealtimeSessionLeaseRecord;
import com.A501.backend.domain.conversation.exception.ConversationErrorCode;
import com.A501.backend.domain.conversation.exception.ConversationException;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class RedisRealtimeSessionLeaseStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RedisRealtimeSessionLeaseStore store;

    @Test
    void Lease를_realtime_session_Key로_저장한다() {
        store = new RedisRealtimeSessionLeaseStore(redisTemplate, objectMapper);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        RealtimeSessionLeaseRecord record = RealtimeSessionLeaseRecord.active("rt_1", 1, 1_000L);

        store.initialize("conv_1", record, Duration.ofSeconds(15));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(keyCaptor.capture(), jsonCaptor.capture(), eq(Duration.ofSeconds(15)));
        assertThat(keyCaptor.getValue()).isEqualTo("realtime:session:conv_1");
        assertThat(jsonCaptor.getValue())
                .contains("\"realtimeSessionId\":\"rt_1\"")
                .contains("\"state\":\"ACTIVE\"");
    }

    @Test
    void Redis_연결_실패는_REDIS_UNAVAILABLE로_변환한다() {
        store = new RedisRealtimeSessionLeaseStore(redisTemplate, objectMapper);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        org.mockito.Mockito.doThrow(new RedisConnectionFailureException("connection refused"))
                .when(valueOperations)
                .set(any(), any(), any(Duration.class));

        assertThatThrownBy(() -> store.initialize(
                        "conv_1",
                        RealtimeSessionLeaseRecord.active("rt_1", 1, 1_000L),
                        Duration.ofSeconds(15)))
                .isInstanceOf(ConversationException.class)
                .extracting(exception -> ((ConversationException) exception).getErrorCode())
                .isEqualTo(ConversationErrorCode.REDIS_UNAVAILABLE);
    }
}
