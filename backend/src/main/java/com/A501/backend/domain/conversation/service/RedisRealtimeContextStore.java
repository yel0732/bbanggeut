package com.A501.backend.domain.conversation.service;

import java.time.Duration;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.A501.backend.domain.conversation.dto.realtime.ContextSnapshot;
import com.A501.backend.domain.conversation.exception.ConversationErrorCode;
import com.A501.backend.domain.conversation.exception.ConversationException;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class RedisRealtimeContextStore implements RealtimeContextStore {

    private static final String KEY_PREFIX = "realtime:context:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void save(String conversationId, ContextSnapshot snapshot, Duration ttl) {
        String key = KEY_PREFIX + conversationId;
        String json = serialize(snapshot);
        try {
            redisTemplate.opsForValue().set(key, json, ttl);
        } catch (DataAccessException exception) {
            throw new ConversationException(ConversationErrorCode.REDIS_UNAVAILABLE);
        }
    }

    @Override
    public void delete(String conversationId) {
        try {
            redisTemplate.delete(KEY_PREFIX + conversationId);
        } catch (DataAccessException exception) {
            throw new ConversationException(ConversationErrorCode.REDIS_UNAVAILABLE);
        }
    }

    private String serialize(ContextSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JacksonException exception) {
            throw new ConversationException(ConversationErrorCode.DIALOGUE_STATE_INVALID);
        }
    }
}
