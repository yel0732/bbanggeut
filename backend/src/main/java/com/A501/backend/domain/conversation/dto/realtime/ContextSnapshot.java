package com.A501.backend.domain.conversation.dto.realtime;

import java.util.List;

/**
 * AI 대화용 Context Snapshot. Realtime Session 시작 전 1회 구성되어
 * {@code realtime:context:{conversationId}}에 저장되고, FastAPI가 매 턴마다
 * Spring API를 호출하지 않고 재사용한다.
 *
 * <p>정확한 생년월일, 부모 이메일, 전체 과거 대화 원문 등 불필요한 개인정보는
 * 이 계약에 필드로 두지 않는다.
 */
public record ContextSnapshot(
        int contextVersion,
        String conversationId,
        RealtimeChildContext child,
        RealtimeConversationContext conversation,
        String recentConversationSummary,
        List<RealtimeTopicSnapshot> topics,
        RealtimePolicySnapshot policy) {

    public static final int CURRENT_VERSION = 1;

    public ContextSnapshot {
        if (contextVersion < 1) {
            throw new IllegalArgumentException("Context Snapshot 버전은 1 이상이어야 합니다.");
        }
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("대화 ID가 필요합니다.");
        }
        if (child == null) {
            throw new IllegalArgumentException("아이 Context가 필요합니다.");
        }
        if (conversation == null) {
            throw new IllegalArgumentException("대화 Context가 필요합니다.");
        }
        if (policy == null) {
            throw new IllegalArgumentException("대화 정책이 필요합니다.");
        }
        topics = topics == null ? List.of() : List.copyOf(topics);
    }

    public static ContextSnapshot create(
            String conversationId,
            RealtimeChildContext child,
            RealtimeConversationContext conversation,
            String recentConversationSummary,
            List<RealtimeTopicSnapshot> topics,
            RealtimePolicySnapshot policy) {
        return new ContextSnapshot(
                CURRENT_VERSION,
                conversationId,
                child,
                conversation,
                recentConversationSummary,
                topics,
                policy);
    }
}
