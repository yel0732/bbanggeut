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

    @Enumerated(EnumType.STRING)
    @Column(name = "topic_type", nullable = false, length = 20)
    private ConversationTopic topicType;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "question_text", columnDefinition = "TEXT")
    private String questionText;

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
            ConversationTopic topicType,
            String content,
            String questionText,
            TurnStatus status,
            String responseId,
            BigDecimal sttConfidence,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            LocalDateTime interruptedAt,
            String interruptionReason,
            String interruptionId) {
        this.turnId = turnId;
        this.conversationId = conversationId;
        this.turnSeq = turnSeq;
        this.speaker = speaker;
        this.topicType = topicType;
        this.content = content;
        this.questionText = questionText;
        this.status = status;
        this.responseId = responseId;
        this.sttConfidence = sttConfidence;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.interruptedAt = interruptedAt;
        this.interruptionReason = interruptionReason;
        this.interruptionId = interruptionId;
    }

    public static ConversationTurn createAiGenerating(
            String turnId,
            String conversationId,
            int turnSeq,
            String content,
            String responseId,
            LocalDateTime startedAt) {
        return createAiGenerating(
                turnId,
                conversationId,
                turnSeq,
                content,
                content,
                responseId,
                startedAt,
                ConversationTopic.DAILY);
    }

    public static ConversationTurn createAiGenerating(
            String turnId,
            String conversationId,
            int turnSeq,
            String content,
            String questionText,
            String responseId,
            LocalDateTime startedAt) {
        return createAiGenerating(
                turnId,
                conversationId,
                turnSeq,
                content,
                questionText,
                responseId,
                startedAt,
                ConversationTopic.DAILY);
    }

    public static ConversationTurn createAiGenerating(
            String turnId,
            String conversationId,
            int turnSeq,
            String content,
            String questionText,
            String responseId,
            LocalDateTime startedAt,
            ConversationTopic topicType) {
        if (responseId == null) {
            throw new IllegalArgumentException("AI 턴은 responseId가 필요합니다.");
        }
        if (startedAt == null) {
            throw new IllegalArgumentException("AI 턴은 startedAt이 필요합니다.");
        }
        if (questionText == null || questionText.isBlank()) {
            throw new IllegalArgumentException("AI 턴은 순수 질문이 필요합니다.");
        }
        if (topicType == null) {
            throw new IllegalArgumentException("AI 턴은 대화 주제가 필요합니다.");
        }
        return ConversationTurn.builder()
                .turnId(turnId)
                .conversationId(conversationId)
                .turnSeq(turnSeq)
                .speaker(Speaker.AI)
                .topicType(topicType)
                .content(content)
                .questionText(questionText)
                .status(TurnStatus.GENERATING)
                .responseId(responseId)
                .startedAt(startedAt)
                .build();
    }

    public static ConversationTurn createChildCompleted(
            String turnId,
            String conversationId,
            int turnSeq,
            String content,
            BigDecimal sttConfidence,
            LocalDateTime startedAt,
            LocalDateTime completedAt) {
        return createChildCompleted(
                turnId,
                conversationId,
                turnSeq,
                content,
                sttConfidence,
                startedAt,
                completedAt,
                ConversationTopic.DAILY);
    }

    public static ConversationTurn createChildCompleted(
            String turnId,
            String conversationId,
            int turnSeq,
            String content,
            BigDecimal sttConfidence,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            ConversationTopic topicType) {
        validateTerminalTime(startedAt, completedAt, "완료");
        if (topicType == null) {
            throw new IllegalArgumentException("아이 턴은 대화 주제가 필요합니다.");
        }
        return ConversationTurn.builder()
                .turnId(turnId)
                .conversationId(conversationId)
                .turnSeq(turnSeq)
                .speaker(Speaker.CHILD)
                .topicType(topicType)
                .content(content)
                .status(TurnStatus.COMPLETED)
                .sttConfidence(sttConfidence)
                .startedAt(startedAt)
                .completedAt(completedAt)
                .build();
    }

    public static ConversationTurn createAiInterrupted(
            String turnId,
            String conversationId,
            int turnSeq,
            String content,
            String responseId,
            LocalDateTime startedAt,
            LocalDateTime interruptedAt,
            String interruptionReason,
            String interruptionId) {
        if (responseId == null) {
            throw new IllegalArgumentException("중단된 턴은 responseId를 가진 AI 턴만 가능합니다.");
        }
        validateTerminalTime(startedAt, interruptedAt, "중단");
        return ConversationTurn.builder()
                .turnId(turnId)
                .conversationId(conversationId)
                .turnSeq(turnSeq)
                .speaker(Speaker.AI)
                .topicType(ConversationTopic.DAILY)
                .content(content)
                .status(TurnStatus.INTERRUPTED)
                .responseId(responseId)
                .startedAt(startedAt)
                .interruptedAt(interruptedAt)
                .interruptionReason(interruptionReason)
                .interruptionId(interruptionId)
                .build();
    }

    public void complete(LocalDateTime completedAt) {
        validateTerminalTime(startedAt, completedAt, "완료");
        if (status == TurnStatus.COMPLETED) {
            return;
        }
        transitionTo(TurnStatus.COMPLETED);
        this.completedAt = completedAt;
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
        validateTerminalTime(startedAt, interruptedAt, "중단");

        transitionTo(TurnStatus.INTERRUPTED);
        this.interruptedAt = interruptedAt;
        this.interruptionReason = interruptionReason;
        this.interruptionId = interruptionId;
    }

    public void fail(LocalDateTime completedAt) {
        validateTerminalTime(startedAt, completedAt, "실패");
        if (status == TurnStatus.FAILED) {
            return;
        }
        transitionTo(TurnStatus.FAILED);
        this.completedAt = completedAt;
    }

    private static void validateTerminalTime(
            LocalDateTime startedAt,
            LocalDateTime terminalAt,
            String terminalType) {
        if (startedAt == null) {
            throw new IllegalArgumentException("턴 시작 시각이 필요합니다.");
        }
        if (terminalAt == null) {
            throw new IllegalArgumentException("턴 " + terminalType + " 시각이 필요합니다.");
        }
        if (terminalAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("턴 " + terminalType + " 시각은 시작 시각보다 빠를 수 없습니다.");
        }
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
