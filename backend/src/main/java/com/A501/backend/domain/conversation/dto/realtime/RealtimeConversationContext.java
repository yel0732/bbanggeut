package com.A501.backend.domain.conversation.dto.realtime;

import com.A501.backend.domain.conversation.entity.ConversationGoal;
import com.A501.backend.domain.conversation.entity.ConversationTopic;

/**
 * Realtime Session 발급 시 FastAPI에 전달되는 대화 수준 Context.
 *
 * <p>{@code lastTurnSeq}는 이 대화에 지금까지 저장된 마지막 {@code turnSeq}다(없으면 0).
 * FastAPI는 재연결 시 이 값의 다음부터 turnSeq를 발급해, 연결 메모리로만 관리하던
 * 순서가 1로 되돌아가 이미 저장된 턴과 충돌하는 문제를 막는다(DIARY-3-W05 §11-A).
 */
public record RealtimeConversationContext(
        ConversationGoal goal,
        String language,
        ConversationTopic currentTopic,
        int totalMaxQuestionCount,
        int questionCount,
        int lastTurnSeq) {

    public RealtimeConversationContext(
            ConversationGoal goal,
            String language,
            ConversationTopic currentTopic,
            int totalMaxQuestionCount,
            int lastTurnSeq) {
        this(goal, language, currentTopic, totalMaxQuestionCount, 0, lastTurnSeq);
    }
}
