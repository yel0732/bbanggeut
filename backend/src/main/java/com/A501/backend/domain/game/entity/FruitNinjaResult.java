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
@Table(name = "fruit_ninja_result")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FruitNinjaResult {

    @Id
    @Column(name = "game_session_id", nullable = false, updatable = false)
    private UUID gameSessionId;

    @Column(name = "rules_version", nullable = false, updatable = false, length = 40)
    private String rulesVersion;

    @Column(name = "total_fruit_count", nullable = false)
    private int totalFruitCount;

    @Column(name = "sliced_fruit_count", nullable = false)
    private int slicedFruitCount;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private FruitNinjaResult(
            UUID gameSessionId,
            String rulesVersion,
            int totalFruitCount,
            int slicedFruitCount) {
        this.gameSessionId = gameSessionId;
        this.rulesVersion = rulesVersion;
        this.totalFruitCount = totalFruitCount;
        this.slicedFruitCount = slicedFruitCount;
    }

    public static FruitNinjaResult create(
            UUID gameSessionId,
            String rulesVersion,
            int totalFruitCount,
            int slicedFruitCount) {
        if (gameSessionId == null || rulesVersion == null || rulesVersion.isBlank()) {
            throw new IllegalArgumentException("게임 세션 ID와 규칙 버전은 필수입니다.");
        }
        if (totalFruitCount < 0 || slicedFruitCount < 0 || slicedFruitCount > totalFruitCount) {
            throw new IllegalArgumentException("후르츠 게임 결과 개수가 올바르지 않습니다.");
        }
        return new FruitNinjaResult(
                gameSessionId, rulesVersion, totalFruitCount, slicedFruitCount);
    }
}
