package com.A501.backend.domain.conversation.service;

import org.springframework.stereotype.Component;

import com.A501.backend.domain.conversation.service.ConversationChildDirectory.ChildRow;

import lombok.RequiredArgsConstructor;

/**
 * child 테이블을 조회해 아이 소유권을 검증한다.
 *
 * <p>local 프로필도 같은 경로를 쓴다. 고정 childId만 통과시키던 mock 검증기로는
 * 실제로 로그인한 보호자의 아이로 대화를 시작할 수 없어, 로컬에서만 통하는 흐름과
 * 배포 흐름이 갈라져 있었다.
 */
@Component
@RequiredArgsConstructor
public class JdbcConversationChildAdapter implements ChildAccessVerifier {

    private final ConversationChildDirectory childDirectory;

    @Override
    public void verifyAccess(Long memberId, Long childId) {
        if (memberId == null || memberId < 1) {
            throw ConversationChildDirectory.childNotFound();
        }
        ChildRow row = childDirectory.find(childId);
        if (!memberId.equals(row.parentId())) {
            throw ConversationChildDirectory.childNotFound();
        }
    }
}
