package com.A501.backend.domain.conversation.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.A501.backend.domain.conversation.entity.ConversationGameSession;

public interface ConversationGameSessionRepository
        extends JpaRepository<ConversationGameSession, String> {

    List<ConversationGameSession> findAllByConversationIdOrderByGameOrderAsc(
            String conversationId);

    long countByConversationId(String conversationId);
}
