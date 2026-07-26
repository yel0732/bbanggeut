package com.A501.backend.domain.conversation.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.A501.backend.domain.conversation.entity.ConversationTopic;
import com.A501.backend.domain.conversation.entity.ConversationTopicState;

import jakarta.persistence.LockModeType;

public interface ConversationTopicStateRepository
        extends JpaRepository<ConversationTopicState, String> {

    List<ConversationTopicState> findAllByConversationIdOrderByTopicOrderAsc(
            String conversationId);

    Optional<ConversationTopicState> findByConversationIdAndTopicType(
            String conversationId,
            ConversationTopic topicType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select topic
            from ConversationTopicState topic
            where topic.conversationId = :conversationId
              and topic.topicType = :topicType
            """)
    Optional<ConversationTopicState> findByConversationIdAndTopicTypeForUpdate(
            @Param("conversationId") String conversationId,
            @Param("topicType") ConversationTopic topicType);
}
