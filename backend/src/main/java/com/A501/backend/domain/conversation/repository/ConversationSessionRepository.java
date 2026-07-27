package com.A501.backend.domain.conversation.repository;

import java.util.Collection;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.ConversationStatus;

import jakarta.persistence.LockModeType;

public interface ConversationSessionRepository extends JpaRepository<ConversationSession, String> {

    boolean existsByChildIdAndStatusIn(Long childId, Collection<ConversationStatus> statuses);

    Optional<ConversationSession> findFirstByChildIdAndStatusInOrderByCreatedAtDesc(
            Long childId,
            Collection<ConversationStatus> statuses);

    List<ConversationSession>
            findAllByChildIdAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThanOrderByCompletedAtAsc(
                    Long childId,
                    ConversationStatus status,
                    LocalDateTime startedAt,
                    LocalDateTime endedAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from ConversationSession session where session.conversationId = :conversationId")
    Optional<ConversationSession> findByIdForUpdate(@Param("conversationId") String conversationId);
}
