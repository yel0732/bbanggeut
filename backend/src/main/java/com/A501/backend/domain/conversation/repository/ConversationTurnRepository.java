package com.A501.backend.domain.conversation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.A501.backend.domain.conversation.entity.ConversationTurn;
import com.A501.backend.domain.conversation.entity.Speaker;

public interface ConversationTurnRepository extends JpaRepository<ConversationTurn, String> {

    Page<ConversationTurn> findByConversationIdOrderByTurnSeqAsc(
            String conversationId,
            Pageable pageable);

    Page<ConversationTurn> findByConversationIdAndSpeakerOrderByTurnSeqAsc(
            String conversationId,
            Speaker speaker,
            Pageable pageable);
}
