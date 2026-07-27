package com.A501.backend.domain.conversation.entity;

import java.time.LocalDateTime;

import com.A501.backend.common.entity.BaseTimeEntity;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "conversation_session",
        indexes = {
                @Index(name = "idx_conversation_session_child_status", columnList = "child_id, status"),
                @Index(name = "idx_conversation_session_status_updated", columnList = "status, updated_at")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConversationSession extends BaseTimeEntity implements Persistable<String> {

    @Id
    @Column(name = "conversation_id", length = 40)
    private String conversationId;

    @Column(name = "child_id", nullable = false)
    private Long childId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ConversationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_topic", nullable = false, length = 20)
    private ConversationTopic currentTopic;

    @Column(name = "question_count", nullable = false)
    private int questionCount;

    @Column(name = "max_question_count", nullable = false)
    private int maxQuestionCount;

    @Column(name = "current_session_epoch", nullable = false)
    private int currentSessionEpoch;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "termination_reason", length = 40)
    private TerminationReason terminationReason;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Builder(access = AccessLevel.PRIVATE)
    private ConversationSession(
            String conversationId,
            Long childId,
            int maxQuestionCount,
            ConversationTopic currentTopic) {
        this.conversationId = conversationId;
        this.childId = childId;
        this.maxQuestionCount = maxQuestionCount;
        this.currentTopic = currentTopic;
        this.status = ConversationStatus.READY;
        this.questionCount = 0;
        this.currentSessionEpoch = 0;
    }

    public static ConversationSession create(
            String conversationId,
            Long childId,
            java.util.UUID gameSessionId,
            ContextType contextType,
            ConversationGoal conversationGoal,
            int maxQuestionCount) {
        return create(
                conversationId,
                childId,
                contextType == ContextType.GAME
                        ? ConversationTopic.GAME
                        : ConversationTopic.DAILY,
                maxQuestionCount);
    }

    public static ConversationSession create(
            String conversationId,
            Long childId,
            ConversationTopic currentTopic,
            int totalMaxQuestionCount) {
        if (currentTopic == null) {
            throw new IllegalArgumentException("현재 대화 주제가 필요합니다.");
        }
        return ConversationSession.builder()
                .conversationId(conversationId)
                .childId(childId)
                .maxQuestionCount(totalMaxQuestionCount)
                .currentTopic(currentTopic)
                .build();
    }

    public void start() {
        transitionTo(ConversationStatus.IN_PROGRESS);
    }

    public void start(LocalDateTime startedAt) {
        if (startedAt == null) {
            throw new IllegalArgumentException("시작 시각이 필요합니다.");
        }
        if (status == ConversationStatus.IN_PROGRESS) {
            return;
        }
        transitionTo(ConversationStatus.IN_PROGRESS);
        this.startedAt = startedAt;
    }

    public void incrementQuestionCount() {
        if (status != ConversationStatus.IN_PROGRESS) {
            throw new IllegalStateException("진행 중인 대화만 질문 수를 증가시킬 수 있습니다.");
        }
        if (questionCount >= maxQuestionCount) {
            throw new IllegalStateException("최대 질문 수를 초과할 수 없습니다.");
        }
        questionCount++;
    }

    /**
     * Realtime Session을 발급하며 재연결 세대(sessionEpoch)를 증가시킨다. 완전한 재연결
     * Epoch 검증·비교는 W02(T07)에서 다루며, 여기서는 발급 시점의 세대 확정만 담당한다.
     */
    public int startRealtimeSession() {
        if (status.isTerminal()) {
            throw new IllegalStateException("종료된 대화 세션에는 Realtime Session을 발급할 수 없습니다.");
        }
        currentSessionEpoch++;
        return currentSessionEpoch;
    }

    public void switchTopic(ConversationTopic nextTopic) {
        if (status != ConversationStatus.IN_PROGRESS) {
            throw new IllegalStateException("진행 중인 대화만 주제를 전환할 수 있습니다.");
        }
        if (nextTopic == null || nextTopic == currentTopic) {
            throw new IllegalArgumentException("다음 대화 주제가 올바르지 않습니다.");
        }
        currentTopic = nextTopic;
    }

    public void terminate(LocalDateTime completedAt, TerminationReason terminationReason) {
        if (completedAt == null) {
            throw new IllegalArgumentException("종료 시각이 필요합니다.");
        }
        if (terminationReason == null) {
            throw new IllegalArgumentException("종료 사유가 필요합니다.");
        }
        if (status.isTerminal()) {
            return;
        }

        transitionTo(terminationReason.getTargetStatus());
        this.completedAt = completedAt;
        this.terminationReason = terminationReason;
    }

    private void transitionTo(ConversationStatus nextStatus) {
        if (status == nextStatus) {
            return;
        }
        if (!status.canTransitionTo(nextStatus)) {
            throw new IllegalStateException("대화 세션 상태를 " + status + "에서 " + nextStatus + "로 변경할 수 없습니다.");
        }
        status = nextStatus;
    }

    @Override
    public String getId() {
        return conversationId;
    }

    @Override
    public boolean isNew() {
        return isNewEntity();
    }
}
