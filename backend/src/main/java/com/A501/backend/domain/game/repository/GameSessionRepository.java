package com.A501.backend.domain.game.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.A501.backend.domain.game.entity.GameSession;

public interface GameSessionRepository extends JpaRepository<GameSession, UUID> {
    Optional<GameSession> findByClientPlayId(UUID clientPlayId);

    List<GameSession> findAllByChildIdAndCompletedAtGreaterThanEqualAndCompletedAtLessThanOrderByCompletedAtAsc(
            Long childId,
            LocalDateTime startedAt,
            LocalDateTime endedAt);

    @Query("select session.gameSessionId from GameSession session where session.childId in :childIds")
    List<UUID> findIdsByChildIds(@Param("childIds") List<Long> childIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from GameSession session where session.childId in :childIds")
    int deleteByChildIds(@Param("childIds") List<Long> childIds);
}
