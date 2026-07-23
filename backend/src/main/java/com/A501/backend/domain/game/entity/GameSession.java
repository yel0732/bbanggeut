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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "game_session",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_game_session_client_play_id", columnNames = "client_play_id")
        })
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameSession {

    @Id
    @Column(name = "game_session_id", nullable = false, updatable = false)
    private UUID gameSessionId;

    @Column(name = "child_id", nullable = false)
    private Long childId;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "client_play_id", nullable = false, updatable = false)
    private UUID clientPlayId;

    @Column(name = "difficulty_code", length = 30)
    private String difficultyCode;

    @Column(name = "selected_at", nullable = false)
    private LocalDateTime selectedAt;

    @Column(name = "play_started_at", nullable = false)
    private LocalDateTime playStartedAt;

    @Column(name = "completed_at", nullable = false)
    private LocalDateTime completedAt;

    @Column(name = "play_duration_seconds", nullable = false)
    private int playDurationSeconds;

    @Column(name = "score", nullable = false)
    private int score;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder(access = AccessLevel.PRIVATE)
    private GameSession(
            UUID gameSessionId,
            Long childId,
            Long gameId,
            UUID clientPlayId,
            String difficultyCode,
            LocalDateTime selectedAt,
            LocalDateTime playStartedAt,
            LocalDateTime completedAt,
            int playDurationSeconds,
            int score) {
        this.gameSessionId = gameSessionId;
        this.childId = childId;
        this.gameId = gameId;
        this.clientPlayId = clientPlayId;
        this.difficultyCode = difficultyCode;
        this.selectedAt = selectedAt;
        this.playStartedAt = playStartedAt;
        this.completedAt = completedAt;
        this.playDurationSeconds = playDurationSeconds;
        this.score = score;
    }

    public static GameSession create(
            Long childId,
            Long gameId,
            UUID clientPlayId,
            String difficultyCode,
            LocalDateTime selectedAt,
            LocalDateTime playStartedAt,
            LocalDateTime completedAt,
            int playDurationSeconds,
            int score) {
        return GameSession.builder()
                .gameSessionId(UUID.randomUUID())
                .childId(childId)
                .gameId(gameId)
                .clientPlayId(clientPlayId)
                .difficultyCode(difficultyCode)
                .selectedAt(selectedAt)
                .playStartedAt(playStartedAt)
                .completedAt(completedAt)
                .playDurationSeconds(playDurationSeconds)
                .score(score)
                .build();
    }
}
