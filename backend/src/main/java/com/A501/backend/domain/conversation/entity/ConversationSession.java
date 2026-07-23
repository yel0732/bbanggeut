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
    private Long gameSessionId;

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

    @Column(name = "termination_reason", length = 40)
    private String terminationReason;

    @Builder(access = AccessLevel.PRIVATE)
    private ConversationSession(
            String conversationId,
            Long childId,
            Long gameSessionId,
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
            Long gameSessionId,
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

    @Override
    public String getId() {
        return conversationId;
    }

    @Override
    public boolean isNew() {
        return isNewEntity();
    }
}
