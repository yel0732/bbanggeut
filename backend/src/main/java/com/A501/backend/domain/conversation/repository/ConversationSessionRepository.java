package com.A501.backend.domain.conversation.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
    /**
     * Realtime Session을 한 번이라도 발급받았고(currentSessionEpoch > 0) 아직 종료되지 않은
     * 세션만 좁힌다. 텍스트 전용 대화는 epoch가 계속 0이라 자동으로 제외된다(Reconciler 대상 조회용).
     */
    List<ConversationSession> findAllByStatusInAndCurrentSessionEpochGreaterThan(
            Collection<ConversationStatus> statuses,
            int currentSessionEpoch);

    List<ConversationSession>
            findAllByChildIdAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThanOrderByCompletedAtAsc(
                    Long childId,
                    ConversationStatus status,
                    LocalDateTime startedAt,
                    LocalDateTime endedAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from ConversationSession session where session.conversationId = :conversationId")
    Optional<ConversationSession> findByIdForUpdate(@Param("conversationId") String conversationId);

    // --- 계정/자녀 파기용 (ConversationPurgeService) ---

    /**
     * 해당 자녀들의 대화 ID. 상태를 가리지 않는다 — 파기는 진행 중·중단된 대화도 대상이다.
     */
    @Query("select session.conversationId from ConversationSession session "
            + "where session.childId in :childIds")
    List<String> findIdsByChildIds(@Param("childIds") List<Long> childIds);

    /**
     * 해당 자녀들의 대화 세션 행을 물리 삭제한다.
     * 하위 테이블(turn·topic_state·game_session)을 먼저 지운 뒤 호출해야 한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ConversationSession session where session.childId in :childIds")
    int deleteByChildIds(@Param("childIds") List<Long> childIds);
}
