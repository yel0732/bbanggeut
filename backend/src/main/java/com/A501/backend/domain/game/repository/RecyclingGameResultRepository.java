package com.A501.backend.domain.game.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.A501.backend.domain.game.entity.RecyclingGameResult;

public interface RecyclingGameResultRepository
        extends JpaRepository<RecyclingGameResult, UUID> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from RecyclingGameResult result where result.gameSessionId in :gameSessionIds")
    int deleteByGameSessionIds(@Param("gameSessionIds") List<UUID> gameSessionIds);
}
