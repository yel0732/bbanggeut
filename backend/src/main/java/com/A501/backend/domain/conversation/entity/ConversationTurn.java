package com.A501.backend.domain.conversation.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.data.domain.Persistable;

import com.A501.backend.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "conversation_turn",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_conversation_turn_seq", columnNames = {"conversation_id", "turn_seq"})
        },
        indexes = {
                @Index(name = "idx_conversation_turn_response_id", columnList = "response_id"),
                @Index(name = "idx_conversation_turn_interruption_id", columnList = "interruption_id")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConversationTurn extends BaseTimeEntity implements Persistable<String> {

    @Id
    @Column(name = "turn_id", length = 40)
    private String turnId;

    @Column(name = "conversation_id", nullable = false, length = 40)
    private String conversationId;

    @Column(name = "turn_seq", nullable = false)
    private int turnSeq;

    @Enumerated(EnumType.STRING)
    @Column(name = "speaker", nullable = false, length = 10)
    private Speaker speaker;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TurnStatus status;

    @Column(name = "response_id", length = 40)
    private String responseId;

    @Column(name = "stt_confidence", precision = 5, scale = 4)
    private BigDecimal sttConfidence;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "interrupted_at")
    private LocalDateTime interruptedAt;

    @Column(name = "interruption_reason", length = 40)
    private String interruptionReason;

    @Column(name = "interruption_id", length = 40)
    private String interruptionId;

    @Builder(access = AccessLevel.PRIVATE)
    private ConversationTurn(
            String turnId,
            String conversationId,
            int turnSeq,
            Speaker speaker,
            String content,
            TurnStatus status,
            String responseId,
            BigDecimal sttConfidence,
            LocalDateTime interruptedAt,
            String interruptionReason,
            String interruptionId) {
        this.turnId = turnId;
        this.conversationId = conversationId;
        this.turnSeq = turnSeq;
        this.speaker = speaker;
        this.content = content;
        this.status = status;
        this.responseId = responseId;
        this.sttConfidence = sttConfidence;
        this.interruptedAt = interruptedAt;
        this.interruptionReason = interruptionReason;
        this.interruptionId = interruptionId;
    }

    public static ConversationTurn create(
            String turnId,
            String conversationId,
            int turnSeq,
            Speaker speaker,
            String content,
            TurnStatus status,
            String responseId,
            BigDecimal sttConfidence) {
        if (status == TurnStatus.INTERRUPTED) {
            throw new IllegalArgumentException("INTERRUPTED 상태는 createInterrupted()를 사용하세요.");
        }
        if (speaker == Speaker.AI && responseId == null) {
            throw new IllegalArgumentException("AI 턴은 responseId가 필요합니다.");
        }
        if (speaker != Speaker.CHILD && sttConfidence != null) {
            throw new IllegalArgumentException("sttConfidence는 CHILD 턴에만 있을 수 있습니다.");
        }
        return ConversationTurn.builder()
                .turnId(turnId)
                .conversationId(conversationId)
                .turnSeq(turnSeq)
                .speaker(speaker)
                .content(content)
                .status(status)
                .responseId(responseId)
                .sttConfidence(sttConfidence)
                .build();
    }

    public static ConversationTurn createInterrupted(
            String turnId,
            String conversationId,
            int turnSeq,
            Speaker speaker,
            String content,
            String responseId,
            LocalDateTime interruptedAt,
            String interruptionReason,
            String interruptionId) {
        if (speaker != Speaker.AI || responseId == null) {
            throw new IllegalArgumentException("중단된 턴은 responseId를 가진 AI 턴만 가능합니다.");
        }
        if (interruptedAt == null) {
            throw new IllegalArgumentException("중단된 턴은 interruptedAt이 필요합니다.");
        }
        return ConversationTurn.builder()
                .turnId(turnId)
                .conversationId(conversationId)
                .turnSeq(turnSeq)
                .speaker(speaker)
                .content(content)
                .status(TurnStatus.INTERRUPTED)
                .responseId(responseId)
                .interruptedAt(interruptedAt)
                .interruptionReason(interruptionReason)
                .interruptionId(interruptionId)
                .build();
    }

    public void complete() {
        transitionTo(TurnStatus.COMPLETED);
    }

    public void interrupt(
            LocalDateTime interruptedAt,
            String interruptionReason,
            String interruptionId) {
        if (status == TurnStatus.INTERRUPTED) {
            return;
        }
        if (speaker != Speaker.AI || responseId == null) {
            throw new IllegalStateException("responseId를 가진 AI 턴만 중단할 수 있습니다.");
        }
        if (interruptedAt == null) {
            throw new IllegalArgumentException("중단된 턴은 interruptedAt이 필요합니다.");
        }

        transitionTo(TurnStatus.INTERRUPTED);
        this.interruptedAt = interruptedAt;
        this.interruptionReason = interruptionReason;
        this.interruptionId = interruptionId;
    }

    public void fail() {
        transitionTo(TurnStatus.FAILED);
    }

    private void transitionTo(TurnStatus nextStatus) {
        if (status == nextStatus) {
            return;
        }
        if (!status.canTransitionTo(nextStatus)) {
            throw new IllegalStateException("대화 턴 상태를 " + status + "에서 " + nextStatus + "로 변경할 수 없습니다.");
        }
        status = nextStatus;
    }

    @Override
    public String getId() {
        return turnId;
    }

    @Override
    public boolean isNew() {
        return isNewEntity();
    }
}
