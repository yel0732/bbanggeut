package com.A501.backend.domain.game.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "fruit_score_config")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FruitScoreConfig {

    public static final int MAX_FRUIT_SCORE = 500;

    @EmbeddedId
    private FruitScoreConfigId id;

    @MapsId("ruleVersionId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_version_id", nullable = false)
    private MovementGameRuleVersion ruleVersion;

    @MapsId("fruitTypeId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fruit_type_id", nullable = false)
    private FruitType fruitType;

    @Column(name = "score", nullable = false)
    private int score;

    private FruitScoreConfig(
            MovementGameRuleVersion ruleVersion,
            FruitType fruitType,
            int score) {
        this.id = new FruitScoreConfigId(
                ruleVersion.getRuleVersionId(),
                fruitType.getFruitTypeId());
        this.ruleVersion = ruleVersion;
        this.fruitType = fruitType;
        this.score = score;
    }

    public static FruitScoreConfig create(
            MovementGameRuleVersion ruleVersion,
            FruitType fruitType,
            int score) {
        if (score < 0 || score > MAX_FRUIT_SCORE) {
            throw new IllegalArgumentException("과일 점수는 0~500점이어야 합니다.");
        }
        if (ruleVersion == null || ruleVersion.getRuleVersionId() == null) {
            throw new IllegalArgumentException("저장된 게임 규칙 버전이 필요합니다.");
        }
        if (fruitType == null || fruitType.getFruitTypeId() == null) {
            throw new IllegalArgumentException("저장된 과일 종류가 필요합니다.");
        }

        return new FruitScoreConfig(ruleVersion, fruitType, score);
    }
}
