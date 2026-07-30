package com.A501.backend.domain.conversation.repository;

import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.A501.backend.domain.conversation.entity.ConversationEventFailure;

public interface ConversationEventFailureRepository
        extends JpaRepository<ConversationEventFailure, Long> {

    boolean existsByRedisRecordId(String redisRecordId);

    @Modifying
    @Query("delete from ConversationEventFailure failure where failure.conversationId in :conversationIds")
    int deleteByConversationIds(@Param("conversationIds") Collection<String> conversationIds);
}
