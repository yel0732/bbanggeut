package com.A501.backend.domain.game.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.A501.backend.domain.game.entity.WordMatchProblemResult;

public interface WordMatchProblemResultRepository
        extends JpaRepository<WordMatchProblemResult, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from WordMatchProblemResult result
            where result.gameSessionId in :sessionIds
            """)
    int deleteByGameSessionIds(@Param("sessionIds") List<UUID> sessionIds);
}
