package com.A501.backend.domain.game.entity;

import com.A501.backend.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "recycling_rule_version")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecyclingRuleVersion extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recycling_rule_version_id")
    private Long recyclingRuleVersionId;

    @Column(name = "version_code", nullable = false, unique = true, length = 40)
    private String versionCode;

    @Column(name = "planned_duration_sec", nullable = false)
    private int plannedDurationSeconds;

    @Column(name = "trash_spawn_interval_ms", nullable = false)
    private int trashSpawnIntervalMs;

    @Column(name = "trash_movement_duration_ms", nullable = false)
    private int trashMovementDurationMs;

    @Column(name = "max_visible_trash", nullable = false)
    private int maxVisibleTrash;

    @Column(name = "score_per_correct", nullable = false)
    private int scorePerCorrect;

    @Column(name = "wrong_guide_duration_ms", nullable = false)
    private int wrongGuideDurationMs;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Builder(access = AccessLevel.PRIVATE)
    private RecyclingRuleVersion(
            String versionCode,
            int plannedDurationSeconds,
            int trashSpawnIntervalMs,
            int trashMovementDurationMs,
            int maxVisibleTrash,
            int scorePerCorrect,
            int wrongGuideDurationMs,
            boolean active) {
        this.versionCode = versionCode;
        this.plannedDurationSeconds = plannedDurationSeconds;
        this.trashSpawnIntervalMs = trashSpawnIntervalMs;
        this.trashMovementDurationMs = trashMovementDurationMs;
        this.maxVisibleTrash = maxVisibleTrash;
        this.scorePerCorrect = scorePerCorrect;
        this.wrongGuideDurationMs = wrongGuideDurationMs;
        this.active = active;
    }

    public static RecyclingRuleVersion create(
            String versionCode,
            int plannedDurationSeconds,
            int trashSpawnIntervalMs,
            int trashMovementDurationMs,
            int maxVisibleTrash,
            int scorePerCorrect,
            int wrongGuideDurationMs,
            boolean active) {
        if (versionCode == null || versionCode.isBlank() || versionCode.length() > 40) {
            throw new IllegalArgumentException("규칙 버전 코드는 1~40자여야 합니다.");
        }
        if (plannedDurationSeconds < 1
                || trashSpawnIntervalMs < 1
                || trashMovementDurationMs < 1
                || maxVisibleTrash < 1
                || scorePerCorrect < 0
                || wrongGuideDurationMs < 0) {
            throw new IllegalArgumentException("분리수거 게임 규칙 값이 올바르지 않습니다.");
        }
        return RecyclingRuleVersion.builder()
                .versionCode(versionCode)
                .plannedDurationSeconds(plannedDurationSeconds)
                .trashSpawnIntervalMs(trashSpawnIntervalMs)
                .trashMovementDurationMs(trashMovementDurationMs)
                .maxVisibleTrash(maxVisibleTrash)
                .scorePerCorrect(scorePerCorrect)
                .wrongGuideDurationMs(wrongGuideDurationMs)
                .active(active)
                .build();
    }
}
