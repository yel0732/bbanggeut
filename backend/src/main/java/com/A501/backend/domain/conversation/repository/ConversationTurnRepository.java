package com.A501.backend.domain.conversation.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.A501.backend.domain.conversation.entity.ConversationTurn;
import com.A501.backend.domain.conversation.entity.Speaker;
import com.A501.backend.domain.conversation.entity.TurnStatus;

public interface ConversationTurnRepository extends JpaRepository<ConversationTurn, String> {

    Page<ConversationTurn> findByConversationIdOrderByTurnSeqAsc(
            String conversationId,
            Pageable pageable);

    Page<ConversationTurn> findByConversationIdAndSpeakerOrderByTurnSeqAsc(
            String conversationId,
            Speaker speaker,
            Pageable pageable);

    List<ConversationTurn> findTop5ByConversationIdAndSpeakerAndStatusOrderByTurnSeqDesc(
            String conversationId,
            Speaker speaker,
            TurnStatus status);

    @Query("""
            select coalesce(max(turn.turnSeq), 0)
            from ConversationTurn turn
            where turn.conversationId = :conversationId
            """)
    int findMaxTurnSeqByConversationId(@Param("conversationId") String conversationId);
}
