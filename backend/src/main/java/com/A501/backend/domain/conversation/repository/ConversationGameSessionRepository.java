package com.A501.backend.domain.conversation.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.A501.backend.domain.conversation.entity.ConversationGameSession;

public interface ConversationGameSessionRepository
        extends JpaRepository<ConversationGameSession, String> {

    List<ConversationGameSession> findAllByConversationIdOrderByGameOrderAsc(
            String conversationId);

    long countByConversationId(String conversationId);

    @Modifying
    @Query("delete from ConversationGameSession link where link.conversationId = :conversationId")
    int deleteByConversationId(@Param("conversationId") String conversationId);
}
