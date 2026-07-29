package com.A501.backend.domain.conversation.service;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.A501.backend.domain.conversation.exception.ConversationErrorCode;
import com.A501.backend.domain.conversation.exception.ConversationException;

import lombok.RequiredArgsConstructor;

/**
 * child 테이블 조회를 한곳에 모은다. 소유권 검증과 프로필 제공이 같은 행을 읽지만
 * 서로 다른 빈으로 나뉘어 있어(테스트가 검증만 대체할 수 있어야 한다) 질의는 여기서
 * 공유한다.
 *
 * <p>미존재·소프트삭제 모두 CHILD_NOT_FOUND로 통일해 다른 보호자의 childId 존재
 * 여부가 새어나가지 않게 한다(IDOR 방지).
 */
@Component
@RequiredArgsConstructor
class ConversationChildDirectory {

    private static final String FIND_CHILD_SQL = """
            select parent_id, name
            from child
            where child_id = ?
              and deleted_at is null
            """;

    private final JdbcTemplate jdbcTemplate;

    ChildRow find(Long childId) {
        if (childId == null || childId < 1) {
            throw childNotFound();
        }
        List<ChildRow> rows = jdbcTemplate.query(
                FIND_CHILD_SQL,
                (resultSet, rowNumber) -> new ChildRow(
                        resultSet.getLong("parent_id"),
                        resultSet.getString("name")),
                childId);
        if (rows.size() != 1) {
            throw childNotFound();
        }
        return rows.getFirst();
    }

    static ConversationException childNotFound() {
        return new ConversationException(ConversationErrorCode.CHILD_NOT_FOUND);
    }

    record ChildRow(Long parentId, String name) {
    }
}
