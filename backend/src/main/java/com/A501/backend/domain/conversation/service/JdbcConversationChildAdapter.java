package com.A501.backend.domain.conversation.service;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.A501.backend.domain.conversation.dto.realtime.RealtimeChildContext;
import com.A501.backend.domain.conversation.exception.ConversationErrorCode;
import com.A501.backend.domain.conversation.exception.ConversationException;

import lombok.RequiredArgsConstructor;

/**
 * child 테이블을 직접 조회해 아이 소유권을 검증하고 Context Snapshot용 프로필을 제공한다.
 * 소유권 관련 모든 실패(미존재·소프트삭제·보호자 불일치)는 CHILD_NOT_FOUND로 통일해
 * 다른 보호자의 childId 존재 여부가 새어나가지 않게 한다(IDOR 방지).
 * local 프로필은 mock 어댑터가 대체하므로 !local에만 등록한다.
 */
@Component
@Profile("!local")
@RequiredArgsConstructor
public class JdbcConversationChildAdapter
        implements ChildAccessVerifier, RealtimeChildProfileProvider {

    private static final String FIND_CHILD_SQL = """
            select parent_id, name
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
        ChildRow row = findChild(childId);
        if (!memberId.equals(row.parentId())) {
            throw childNotFound();
        }
    }

    @Override
    public RealtimeChildContext getProfile(Long childId) {
        if (childId == null || childId < 1) {
            throw childNotFound();
        }
        return new RealtimeChildContext(findChild(childId).name());
    }

    private ChildRow findChild(Long childId) {
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

    private ConversationException childNotFound() {
        return new ConversationException(ConversationErrorCode.CHILD_NOT_FOUND);
    }

    private record ChildRow(Long parentId, String name) {
    }
}
