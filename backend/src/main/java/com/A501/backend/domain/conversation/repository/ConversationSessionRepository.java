package com.A501.backend.domain.conversation.repository;

import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;

import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.ConversationStatus;

public interface ConversationSessionRepository extends JpaRepository<ConversationSession, String> {

    boolean existsByChildIdAndStatusIn(Long childId, Collection<ConversationStatus> statuses);
}
