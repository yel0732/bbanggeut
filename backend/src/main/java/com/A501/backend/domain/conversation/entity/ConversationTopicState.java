package com.A501.backend.domain.conversation.entity;

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
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "conversation_topic_state",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_conversation_topic_type",
                        columnNames = {"conversation_id", "topic_type"}),
                @UniqueConstraint(
                        name = "uk_conversation_topic_order",
                        columnNames = {"conversation_id", "topic_order"})
        },
        indexes = {
                @Index(
                        name = "idx_conversation_topic_status",
                        columnList = "conversation_id, status")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConversationTopicState extends BaseTimeEntity
        implements Persistable<String> {

    @Id
    @Column(name = "conversation_topic_id", length = 50)
    private String conversationTopicId;

    @Column(name = "conversation_id", nullable = false, length = 40)
    private String conversationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "topic_type", nullable = false, length = 20)
    private ConversationTopic topicType;

    @Column(name = "topic_order", nullable = false)
    private int topicOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ConversationTopicStatus status;

    @Column(name = "question_count", nullable = false)
    private int questionCount;

    @Column(name = "max_question_count", nullable = false)
    private int maxQuestionCount;

    @Column(name = "slots_json", nullable = false, columnDefinition = "TEXT")
    private String slotsJson;

    @Column(name = "context_json", nullable = false, columnDefinition = "TEXT")
    private String contextJson;

    @Column(name = "skipped_targets_json", nullable = false, columnDefinition = "TEXT")
    private String skippedTargetsJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "completion_reason", length = 40)
    private TerminationReason completionReason;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Builder(access = AccessLevel.PRIVATE)
    private ConversationTopicState(
            String conversationTopicId,
            String conversationId,
            ConversationTopic topicType,
            int topicOrder,
            int maxQuestionCount,
            String slotsJson,
            String contextJson) {
        this.conversationTopicId = requireText(
                conversationTopicId,
                "대화 주제 ID가 필요합니다.");
        this.conversationId = requireText(
                conversationId,
                "대화 ID가 필요합니다.");
        if (topicType == null) {
            throw new IllegalArgumentException("대화 주제가 필요합니다.");
        }
        if (topicOrder < 1) {
            throw new IllegalArgumentException("대화 주제 순서는 1 이상이어야 합니다.");
        }
        if (maxQuestionCount < 1) {
            throw new IllegalArgumentException("최대 질문 수는 1 이상이어야 합니다.");
        }
        this.topicType = topicType;
        this.topicOrder = topicOrder;
        this.maxQuestionCount = maxQuestionCount;
        this.slotsJson = requireText(slotsJson, "슬롯 JSON이 필요합니다.");
        this.contextJson = requireText(contextJson, "주제 Context JSON이 필요합니다.");
        this.skippedTargetsJson = "[]";
        this.status = ConversationTopicStatus.PENDING;
        this.questionCount = 0;
    }

    public static ConversationTopicState create(
            String conversationTopicId,
            String conversationId,
            ConversationTopic topicType,
            int topicOrder,
            int maxQuestionCount,
            String slotsJson,
            String contextJson) {
        return ConversationTopicState.builder()
                .conversationTopicId(conversationTopicId)
                .conversationId(conversationId)
                .topicType(topicType)
                .topicOrder(topicOrder)
                .maxQuestionCount(maxQuestionCount)
                .slotsJson(slotsJson)
                .contextJson(contextJson)
                .build();
    }

    public void start(LocalDateTime startedAt) {
        if (startedAt == null) {
            throw new IllegalArgumentException("주제 시작 시각이 필요합니다.");
        }
        if (status == ConversationTopicStatus.IN_PROGRESS) {
            return;
        }
        if (status != ConversationTopicStatus.PENDING) {
            throw new IllegalStateException("완료된 대화 주제는 다시 시작할 수 없습니다.");
        }
        status = ConversationTopicStatus.IN_PROGRESS;
        this.startedAt = startedAt;
    }

    public void incrementQuestionCount() {
        if (status != ConversationTopicStatus.IN_PROGRESS) {
            throw new IllegalStateException("진행 중인 주제만 질문 수를 증가시킬 수 있습니다.");
        }
        if (questionCount >= maxQuestionCount) {
            throw new IllegalStateException("주제의 최대 질문 수를 초과할 수 없습니다.");
        }
        questionCount++;
    }

    public void updateSlots(String slotsJson) {
        this.slotsJson = requireText(slotsJson, "슬롯 JSON이 필요합니다.");
    }

    public void updateSkippedTargets(String skippedTargetsJson) {
        this.skippedTargetsJson = requireText(
                skippedTargetsJson,
                "Skipped targets JSON is required.");
    }

    public void synchronizeQuestionCount(int count) {
        if (count < 0 || count > maxQuestionCount) {
            throw new IllegalArgumentException("Question count is outside the topic limit.");
        }
        if (count > questionCount) {
            questionCount = count;
        }
    }

    public void complete(
            LocalDateTime completedAt,
            TerminationReason completionReason) {
        if (completedAt == null || completionReason == null) {
            throw new IllegalArgumentException("주제 완료 시각과 사유가 필요합니다.");
        }
        if (status == ConversationTopicStatus.COMPLETED) {
            return;
        }
        if (status == ConversationTopicStatus.PENDING) {
            this.startedAt = completedAt;
        }
        status = ConversationTopicStatus.COMPLETED;
        this.completedAt = completedAt;
        this.completionReason = completionReason;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    @Override
    public String getId() {
        return conversationTopicId;
    }

    @Override
    public boolean isNew() {
        return isNewEntity();
    }
}
