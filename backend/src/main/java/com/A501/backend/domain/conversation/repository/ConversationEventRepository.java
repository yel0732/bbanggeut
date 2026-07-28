package com.A501.backend.domain.conversation.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.A501.backend.domain.conversation.entity.ConversationEvent;

public interface ConversationEventRepository extends JpaRepository<ConversationEvent, String> {

    boolean existsByRedisRecordId(String redisRecordId);
}
