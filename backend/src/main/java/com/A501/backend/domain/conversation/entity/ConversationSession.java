package com.A501.backend.domain.conversation.entity;

import java.time.LocalDateTime;
import java.util.UUID;

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
                @Index(name = "idx_conversation_session_game_session", columnList = "game_session_id"),
                @Index(name = "idx_conversation_session_status_updated", columnList = "status, updated_at")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConversationSession extends BaseTimeEntity implements Persistable<String> {

    @Id
    @Column(name = "conversation_id", length = 40)
    private String conversationId;

    @Column(name = "child_id", nullable = false)
    private Long childId;

    @Column(name = "game_session_id")
    private UUID gameSessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ConversationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "context_type", nullable = false, length = 20)
    private ContextType contextType;

    @Enumerated(EnumType.STRING)
    @Column(name = "conversation_goal", nullable = false, length = 20)
    private ConversationGoal conversationGoal;

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
            UUID gameSessionId,
            ContextType contextType,
            ConversationGoal conversationGoal,
            int maxQuestionCount) {
        this.conversationId = conversationId;
        this.childId = childId;
        this.gameSessionId = gameSessionId;
        this.contextType = contextType;
        this.conversationGoal = conversationGoal;
        this.maxQuestionCount = maxQuestionCount;
        this.status = ConversationStatus.READY;
        this.questionCount = 0;
        this.currentSessionEpoch = 0;
    }

    public static ConversationSession create(
            String conversationId,
            Long childId,
            UUID gameSessionId,
            ContextType contextType,
            ConversationGoal conversationGoal,
            int maxQuestionCount) {
        return ConversationSession.builder()
                .conversationId(conversationId)
                .childId(childId)
                .gameSessionId(gameSessionId)
                .contextType(contextType)
                .conversationGoal(conversationGoal)
                .maxQuestionCount(maxQuestionCount)
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
