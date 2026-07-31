package com.A501.backend.domain.game.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "recycling_game_result")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecyclingGameResult {

    @Id
    @Column(name = "game_session_id", nullable = false, updatable = false)
    private UUID gameSessionId;

    @Column(name = "rules_version", nullable = false, updatable = false, length = 40)
    private String rulesVersion;

    @Column(name = "successful_count", nullable = false)
    private int successfulCount;

    @Column(name = "wrong_count", nullable = false)
    private int wrongCount;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private RecyclingGameResult(
            UUID gameSessionId,
            String rulesVersion,
            int successfulCount,
            int wrongCount) {
        this.gameSessionId = gameSessionId;
        this.rulesVersion = rulesVersion;
        this.successfulCount = successfulCount;
        this.wrongCount = wrongCount;
    }

    public static RecyclingGameResult create(
            UUID gameSessionId,
            String rulesVersion,
            int successfulCount,
            int wrongCount) {
        if (gameSessionId == null) {
            throw new IllegalArgumentException("게임 세션 ID는 필수입니다.");
        }
        if (rulesVersion == null || rulesVersion.isBlank() || rulesVersion.length() > 40) {
            throw new IllegalArgumentException("분리수거 규칙 버전은 1~40자여야 합니다.");
        }
        if (successfulCount < 0 || wrongCount < 0) {
            throw new IllegalArgumentException("성공 및 오답 개수는 0 이상이어야 합니다.");
        }
        return new RecyclingGameResult(
                gameSessionId,
                rulesVersion,
                successfulCount,
                wrongCount);
    }
}
