package com.A501.backend.domain.conversation.service;

import java.time.Duration;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.A501.backend.domain.conversation.dto.realtime.RealtimeSessionLeaseRecord;
import com.A501.backend.domain.conversation.exception.ConversationErrorCode;
import com.A501.backend.domain.conversation.exception.ConversationException;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class RedisRealtimeSessionLeaseStore implements RealtimeSessionLeaseStore {

    private static final String KEY_PREFIX = "realtime:session:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void initialize(String conversationId, RealtimeSessionLeaseRecord record, Duration ttl) {
        String key = KEY_PREFIX + conversationId;
        String json = serialize(record);
        try {
            redisTemplate.opsForValue().set(key, json, ttl);
        } catch (DataAccessException exception) {
            throw new ConversationException(ConversationErrorCode.REDIS_UNAVAILABLE);
        }
    }

    private String serialize(RealtimeSessionLeaseRecord record) {
        try {
            return objectMapper.writeValueAsString(record);
        } catch (JacksonException exception) {
            throw new ConversationException(ConversationErrorCode.DIALOGUE_STATE_INVALID);
        }
    }
}
