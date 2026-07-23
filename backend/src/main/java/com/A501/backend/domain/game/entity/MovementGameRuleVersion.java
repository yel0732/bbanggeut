package com.A501.backend.domain.game.entity;

import java.math.BigDecimal;

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
@Table(name = "game_rule_version")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MovementGameRuleVersion extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rule_version_id")
    private Long ruleVersionId;

    @Column(name = "version_code", nullable = false, unique = true, length = 40)
    private String versionCode;

    @Column(name = "planned_duration_sec", nullable = false)
    private int plannedDurationSeconds;

    @Column(name = "slice_speed_threshold", nullable = false, precision = 10, scale = 4)
    private BigDecimal sliceSpeedThreshold;

    @Column(name = "fruit_spawn_interval_ms", nullable = false)
    private int fruitSpawnIntervalMs;

    @Column(name = "max_visible_fruit", nullable = false)
    private int maxVisibleFruit;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Builder(access = AccessLevel.PRIVATE)
    private MovementGameRuleVersion(
            String versionCode,
            int plannedDurationSeconds,
            BigDecimal sliceSpeedThreshold,
            int fruitSpawnIntervalMs,
            int maxVisibleFruit,
            boolean active) {
        this.versionCode = versionCode;
        this.plannedDurationSeconds = plannedDurationSeconds;
        this.sliceSpeedThreshold = sliceSpeedThreshold;
        this.fruitSpawnIntervalMs = fruitSpawnIntervalMs;
        this.maxVisibleFruit = maxVisibleFruit;
        this.active = active;
    }

    public static MovementGameRuleVersion create(
            String versionCode,
            int plannedDurationSeconds,
            BigDecimal sliceSpeedThreshold,
            int fruitSpawnIntervalMs,
            int maxVisibleFruit,
            boolean active) {
        validate(
                versionCode,
                plannedDurationSeconds,
                sliceSpeedThreshold,
                fruitSpawnIntervalMs,
                maxVisibleFruit);

        return MovementGameRuleVersion.builder()
                .versionCode(versionCode)
                .plannedDurationSeconds(plannedDurationSeconds)
                .sliceSpeedThreshold(sliceSpeedThreshold)
                .fruitSpawnIntervalMs(fruitSpawnIntervalMs)
                .maxVisibleFruit(maxVisibleFruit)
                .active(active)
                .build();
    }

    private static void validate(
            String versionCode,
            int plannedDurationSeconds,
            BigDecimal sliceSpeedThreshold,
            int fruitSpawnIntervalMs,
            int maxVisibleFruit) {
        if (versionCode == null || versionCode.isBlank() || versionCode.length() > 40) {
            throw new IllegalArgumentException("게임 규칙 버전 코드는 1~40자여야 합니다.");
        }
        if (plannedDurationSeconds < 1) {
            throw new IllegalArgumentException("게임 제한 시간은 1초 이상이어야 합니다.");
        }
        if (sliceSpeedThreshold == null || sliceSpeedThreshold.signum() <= 0) {
            throw new IllegalArgumentException("베기 기준 속도는 0보다 커야 합니다.");
        }
        if (fruitSpawnIntervalMs < 1) {
            throw new IllegalArgumentException("과일 생성 간격은 1ms 이상이어야 합니다.");
        }
        if (maxVisibleFruit < 1) {
            throw new IllegalArgumentException("최대 표시 과일 수는 1개 이상이어야 합니다.");
        }
    }
}
