package com.A501.backend.domain.conversation.entity;

import java.time.LocalDateTime;

import com.A501.backend.common.entity.BaseTimeEntity;
import com.A501.backend.domain.conversation.dto.stream.ConversationStreamEventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "conversation_event_failure",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_conversation_event_failure_record",
                        columnNames = "redis_record_id")
        },
        indexes = {
                @Index(
                        name = "idx_conversation_event_failure_record",
                        columnList = "redis_record_id")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConversationEventFailure extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "conversation_event_failure_id")
    private Long id;

    @Column(name = "redis_record_id", nullable = false, length = 80)
    private String redisRecordId;

    @Column(name = "event_id", length = 80)
    private String eventId;

    @Column(name = "conversation_id", length = 40)
    private String conversationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 40)
    private ConversationStreamEventType eventType;

    @Column(name = "delivery_count", nullable = false)
    private long deliveryCount;

    @Column(name = "failed_at", nullable = false)
    private LocalDateTime failedAt;

    @Column(name = "failure_reason", nullable = false, length = 1000)
    private String failureReason;

    private ConversationEventFailure(
            String redisRecordId,
            String eventId,
            String conversationId,
            ConversationStreamEventType eventType,
            long deliveryCount,
            LocalDateTime failedAt,
            String failureReason) {
        if (redisRecordId == null || redisRecordId.isBlank()) {
            throw new IllegalArgumentException("Redis Record ID가 필요합니다.");
        }
        if (deliveryCount < 1 || failedAt == null) {
            throw new IllegalArgumentException("Event 실패 횟수와 시각이 필요합니다.");
        }
        if (failureReason == null || failureReason.isBlank() || failureReason.length() > 1000) {
            throw new IllegalArgumentException("실패 사유는 1~1000자여야 합니다.");
        }
        this.redisRecordId = redisRecordId;
        this.eventId = eventId;
        this.conversationId = conversationId;
        this.eventType = eventType;
        this.deliveryCount = deliveryCount;
        this.failedAt = failedAt;
        this.failureReason = failureReason;
    }

    public static ConversationEventFailure record(
            String redisRecordId,
            String eventId,
            String conversationId,
            ConversationStreamEventType eventType,
            long deliveryCount,
            LocalDateTime failedAt,
            String failureReason) {
        return new ConversationEventFailure(
                redisRecordId,
                eventId,
                conversationId,
                eventType,
                deliveryCount,
                failedAt,
                failureReason);
    }
}
