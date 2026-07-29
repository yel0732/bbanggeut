package com.A501.backend.domain.conversation.entity;

import java.time.LocalDateTime;

import org.springframework.data.domain.Persistable;

import com.A501.backend.common.entity.BaseTimeEntity;
import com.A501.backend.domain.conversation.dto.stream.ConversationStreamEvent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
        name = "conversation_event",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_conversation_event_redis_record",
                        columnNames = "redis_record_id")
        },
        indexes = {
                @Index(
                        name = "idx_conversation_event_conversation_epoch",
                        columnList = "conversation_id, session_epoch"),
                @Index(
                        name = "idx_conversation_event_turn",
                        columnList = "conversation_id, turn_id")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConversationEvent extends BaseTimeEntity implements Persistable<String> {

    @Id
    @Column(name = "event_id", length = 80)
    private String eventId;

    @Column(name = "redis_record_id", nullable = false, length = 80)
    private String redisRecordId;

    @Column(name = "conversation_id", nullable = false, length = 40)
    private String conversationId;

    @Column(name = "realtime_session_id", nullable = false, length = 80)
    private String realtimeSessionId;

    @Column(name = "session_epoch", nullable = false)
    private int sessionEpoch;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private com.A501.backend.domain.conversation.dto.stream.ConversationStreamEventType eventType;

    @Column(name = "turn_id", length = 40)
    private String turnId;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_result", nullable = false, length = 30)
    private ConversationEventProcessingResult processingResult;

    private ConversationEvent(
            String eventId,
            String redisRecordId,
            String conversationId,
            String realtimeSessionId,
            int sessionEpoch,
            com.A501.backend.domain.conversation.dto.stream.ConversationStreamEventType eventType,
            String turnId,
            LocalDateTime occurredAt,
            LocalDateTime processedAt,
            ConversationEventProcessingResult processingResult) {
        this.eventId = requireText(eventId, "Event ID가 필요합니다.");
        this.redisRecordId = requireText(redisRecordId, "Redis Record ID가 필요합니다.");
        this.conversationId = requireText(conversationId, "대화 ID가 필요합니다.");
        this.realtimeSessionId = requireText(realtimeSessionId, "Realtime Session ID가 필요합니다.");
        if (sessionEpoch < 1) {
            throw new IllegalArgumentException("Session Epoch는 1 이상이어야 합니다.");
        }
        if (eventType == null || occurredAt == null || processedAt == null || processingResult == null) {
            throw new IllegalArgumentException("Event 처리 메타데이터가 필요합니다.");
        }
        this.sessionEpoch = sessionEpoch;
        this.eventType = eventType;
        this.turnId = turnId;
        this.occurredAt = occurredAt;
        this.processedAt = processedAt;
        this.processingResult = processingResult;
    }

    public static ConversationEvent record(
            String redisRecordId,
            ConversationStreamEvent event,
            LocalDateTime occurredAt,
            LocalDateTime processedAt,
            ConversationEventProcessingResult processingResult) {
        return new ConversationEvent(
                event.eventId(),
                redisRecordId,
                event.conversationId(),
                event.realtimeSessionId(),
                event.sessionEpoch(),
                event.eventType(),
                event.turnId(),
                occurredAt,
                processedAt,
                processingResult);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    @Override
    public String getId() {
        return eventId;
    }

    @Override
    public boolean isNew() {
        return isNewEntity();
    }
}
