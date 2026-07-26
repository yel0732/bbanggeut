package com.A501.backend.domain.conversation.entity;

import java.util.UUID;

import org.springframework.data.domain.Persistable;

import com.A501.backend.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "conversation_game_session",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_conversation_game_session",
                        columnNames = {"conversation_id", "game_session_id"}),
                @UniqueConstraint(
                        name = "uk_conversation_game_order",
                        columnNames = {"conversation_id", "game_order"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConversationGameSession extends BaseTimeEntity
        implements Persistable<String> {

    @Id
    @Column(name = "conversation_game_session_id", length = 80)
    private String conversationGameSessionId;

    @Column(name = "conversation_id", nullable = false, length = 40)
    private String conversationId;

    @Column(name = "game_session_id", nullable = false)
    private UUID gameSessionId;

    @Column(name = "game_order", nullable = false)
    private int gameOrder;

    @Builder(access = AccessLevel.PRIVATE)
    private ConversationGameSession(
            String conversationGameSessionId,
            String conversationId,
            UUID gameSessionId,
            int gameOrder) {
        if (conversationGameSessionId == null
                || conversationGameSessionId.isBlank()
                || conversationId == null
                || conversationId.isBlank()
                || gameSessionId == null
                || gameOrder < 1) {
            throw new IllegalArgumentException("대화 게임 연결 정보가 올바르지 않습니다.");
        }
        this.conversationGameSessionId = conversationGameSessionId;
        this.conversationId = conversationId;
        this.gameSessionId = gameSessionId;
        this.gameOrder = gameOrder;
    }

    public static ConversationGameSession create(
            String conversationGameSessionId,
            String conversationId,
            UUID gameSessionId,
            int gameOrder) {
        return ConversationGameSession.builder()
                .conversationGameSessionId(conversationGameSessionId)
                .conversationId(conversationId)
                .gameSessionId(gameSessionId)
                .gameOrder(gameOrder)
                .build();
    }

    @Override
    public String getId() {
        return conversationGameSessionId;
    }

    @Override
    public boolean isNew() {
        return isNewEntity();
    }
}
