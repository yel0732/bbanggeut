package com.A501.backend.domain.game.service;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.A501.backend.domain.game.exception.GameErrorCode;
import com.A501.backend.domain.game.exception.GameException;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JdbcGameChildAccessVerifier implements GameChildAccessVerifier {

    private static final String FIND_CHILD_SQL = """
            select parent_id
            from child
            where child_id = ?
              and deleted_at is null
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void verifyAccess(Long memberId, Long childId) {
        if (memberId == null || memberId < 1 || childId == null || childId < 1) {
            throw childNotFound();
        }
        List<Long> parentIds = jdbcTemplate.query(
                FIND_CHILD_SQL,
                (resultSet, rowNumber) -> resultSet.getLong("parent_id"),
                childId);
        if (parentIds.size() != 1 || !memberId.equals(parentIds.getFirst())) {
            throw childNotFound();
        }
    }

    private GameException childNotFound() {
        return new GameException(GameErrorCode.CHILD_NOT_FOUND);
    }
}
