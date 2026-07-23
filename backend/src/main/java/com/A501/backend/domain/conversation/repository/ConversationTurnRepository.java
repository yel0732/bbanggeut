package com.A501.backend.domain.conversation.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.A501.backend.domain.conversation.entity.ConversationTurn;

public interface ConversationTurnRepository extends JpaRepository<ConversationTurn, String> {
}
