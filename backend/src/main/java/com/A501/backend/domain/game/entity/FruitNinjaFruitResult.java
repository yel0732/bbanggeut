package com.A501.backend.domain.game.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.A501.backend.domain.game.metric.FruitResultCount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "fruit_ninja_fruit_result",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_fruit_ninja_fruit_session_code",
                columnNames = {"game_session_id", "fruit_code"}))
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FruitNinjaFruitResult {

    @Id
    @Column(name = "fruit_result_id", nullable = false, updatable = false)
    private UUID fruitResultId;

    @Column(name = "game_session_id", nullable = false, updatable = false)
    private UUID gameSessionId;

    @Column(name = "fruit_code", nullable = false, updatable = false, length = 40)
    private String fruitCode;

    @Column(name = "generated_count", nullable = false)
    private int generatedCount;

    @Column(name = "sliced_count", nullable = false)
    private int slicedCount;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private FruitNinjaFruitResult(UUID gameSessionId, FruitResultCount result) {
        this.fruitResultId = UUID.randomUUID();
        this.gameSessionId = gameSessionId;
        this.fruitCode = result.fruitCode();
        this.generatedCount = result.generatedCount();
        this.slicedCount = result.slicedCount();
    }

    public static FruitNinjaFruitResult create(
            UUID gameSessionId,
            FruitResultCount result) {
        if (gameSessionId == null || result == null) {
            throw new IllegalArgumentException("게임 세션과 과일 결과는 필수입니다.");
        }
        return new FruitNinjaFruitResult(gameSessionId, result);
    }
}
