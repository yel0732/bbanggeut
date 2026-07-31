package com.A501.backend.domain.game.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.A501.backend.domain.game.entity.WordMatchResult;

public interface WordMatchResultRepository extends JpaRepository<WordMatchResult, UUID> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from WordMatchResult result where result.gameSessionId in :sessionIds")
    int deleteByGameSessionIds(@Param("sessionIds") List<UUID> sessionIds);
}
