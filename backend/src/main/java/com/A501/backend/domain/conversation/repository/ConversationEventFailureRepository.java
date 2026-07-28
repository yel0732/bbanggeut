package com.A501.backend.domain.conversation.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.A501.backend.domain.conversation.entity.ConversationEventFailure;

public interface ConversationEventFailureRepository
        extends JpaRepository<ConversationEventFailure, Long> {

    boolean existsByRedisRecordId(String redisRecordId);
}
