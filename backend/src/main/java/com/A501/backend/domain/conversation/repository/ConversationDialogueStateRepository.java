package com.A501.backend.domain.conversation.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.A501.backend.domain.conversation.entity.ConversationDialogueState;

public interface ConversationDialogueStateRepository
        extends JpaRepository<ConversationDialogueState, String> {
}
