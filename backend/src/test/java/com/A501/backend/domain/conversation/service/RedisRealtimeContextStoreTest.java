package com.A501.backend.domain.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.A501.backend.domain.conversation.dto.realtime.ContextSnapshot;
import com.A501.backend.domain.conversation.dto.realtime.RealtimeChildContext;
import com.A501.backend.domain.conversation.dto.realtime.RealtimeConversationContext;
import com.A501.backend.domain.conversation.dto.realtime.RealtimePolicySnapshot;
import com.A501.backend.domain.conversation.entity.ConversationGoal;
import com.A501.backend.domain.conversation.entity.ConversationTopic;
import com.A501.backend.domain.conversation.exception.ConversationErrorCode;
import com.A501.backend.domain.conversation.exception.ConversationException;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class RedisRealtimeContextStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RedisRealtimeContextStore store;

    @Test
    void Context_Snapshot을_realtime_context_Key로_저장한다() {
        store = new RedisRealtimeContextStore(redisTemplate, objectMapper);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ContextSnapshot snapshot = snapshot();

        store.save("conv_redis_test", snapshot, Duration.ofMinutes(30));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(keyCaptor.capture(), jsonCaptor.capture(), eq(Duration.ofMinutes(30)));
        assertThat(keyCaptor.getValue()).isEqualTo("realtime:context:conv_redis_test");
        assertThat(jsonCaptor.getValue()).contains("\"conversationId\":\"conv_redis_test\"");
    }

    @Test
    void Redis_연결_실패는_REDIS_UNAVAILABLE로_변환한다() {
        store = new RedisRealtimeContextStore(redisTemplate, objectMapper);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        org.mockito.Mockito.doThrow(new RedisConnectionFailureException("connection refused"))
                .when(valueOperations)
                .set(any(), any(), any(Duration.class));

        assertThatThrownBy(() -> store.save("conv_redis_fail", snapshot(), Duration.ofMinutes(30)))
                .isInstanceOf(ConversationException.class)
                .extracting(exception -> ((ConversationException) exception).getErrorCode())
                .isEqualTo(ConversationErrorCode.REDIS_UNAVAILABLE);
    }

    private ContextSnapshot snapshot() {
        return ContextSnapshot.create(
                "conv_redis_test",
                new RealtimeChildContext("하준"),
                new RealtimeConversationContext(ConversationGoal.DIARY, "ko-KR", ConversationTopic.DAILY, 6),
                null,
                List.of(),
                RealtimePolicySnapshot.defaultPolicy());
    }
}
