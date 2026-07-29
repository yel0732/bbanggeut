package com.A501.backend.domain.conversation.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.A501.backend.domain.conversation.entity.ConversationTurn;
import com.A501.backend.domain.conversation.entity.ConversationTopic;
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

    List<ConversationTurn>
            findTop5ByConversationIdAndTopicTypeAndSpeakerAndStatusOrderByTurnSeqDesc(
                    String conversationId,
                    ConversationTopic topicType,
                    Speaker speaker,
                    TurnStatus status);

    @Query("""
            select coalesce(max(turn.turnSeq), 0)
            from ConversationTurn turn
            where turn.conversationId = :conversationId
            """)
    int findMaxTurnSeqByConversationId(@Param("conversationId") String conversationId);

    @Query("""
            select coalesce(max(turn.turnSeq), 0)
            from ConversationTurn turn
            where turn.conversationId = :conversationId
              and turn.speaker = :speaker
            """)
    int findMaxTurnSeqByConversationIdAndSpeaker(
            @Param("conversationId") String conversationId,
            @Param("speaker") Speaker speaker);

    Optional<ConversationTurn> findByConversationIdAndTurnSeq(
            String conversationId,
            int turnSeq);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select turn from ConversationTurn turn where turn.turnId = :turnId")
    Optional<ConversationTurn> findByIdForUpdate(@Param("turnId") String turnId);

    @Modifying
    @Query("delete from ConversationTurn turn where turn.conversationId = :conversationId")
    int deleteByConversationId(@Param("conversationId") String conversationId);
}
