package com.A501.backend.domain.game.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.A501.backend.domain.game.metric.CardResultStatus;
import com.A501.backend.domain.game.metric.CognitiveGrade;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
        name = "card_round",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_card_round_session_round",
                        columnNames = {"game_session_id", "round_no"})
        })
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CardRound {

    @Id
    @Column(name = "card_round_id", nullable = false, updatable = false)
    private UUID cardRoundId;

    @Column(name = "game_session_id", nullable = false, updatable = false)
    private UUID gameSessionId;

    @Column(name = "round_no", nullable = false, updatable = false)
    private int roundNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CardResultStatus status;

    @Column(name = "comparison_count", nullable = false)
    private int comparisonCount;

    @Column(name = "wrong_count", nullable = false)
    private int wrongCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "final_grade", length = 20)
    private CognitiveGrade finalGrade;

    @Column(name = "solve_time_ms")
    private Long solveTimeMs;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder(access = AccessLevel.PRIVATE)
    private CardRound(
            UUID cardRoundId,
            UUID gameSessionId,
            int roundNo,
            CardResultStatus status,
            int comparisonCount,
            int wrongCount,
            CognitiveGrade finalGrade,
            Long solveTimeMs) {
        this.cardRoundId = cardRoundId;
        this.gameSessionId = gameSessionId;
        this.roundNo = roundNo;
        this.status = status;
        this.comparisonCount = comparisonCount;
        this.wrongCount = wrongCount;
        this.finalGrade = finalGrade;
        this.solveTimeMs = solveTimeMs;
    }

    public static CardRound create(
            UUID gameSessionId,
            int roundNo,
            CardResultStatus status,
            int comparisonCount,
            int wrongCount,
            CognitiveGrade finalGrade,
            Long solveTimeMs) {
        validate(gameSessionId, roundNo, status, comparisonCount, wrongCount, finalGrade, solveTimeMs);

        return CardRound.builder()
                .cardRoundId(UUID.randomUUID())
                .gameSessionId(gameSessionId)
                .roundNo(roundNo)
                .status(status)
                .comparisonCount(comparisonCount)
                .wrongCount(wrongCount)
                .finalGrade(finalGrade)
                .solveTimeMs(solveTimeMs)
                .build();
    }

    private static void validate(
            UUID gameSessionId,
            int roundNo,
            CardResultStatus status,
            int comparisonCount,
            int wrongCount,
            CognitiveGrade finalGrade,
            Long solveTimeMs) {
        if (gameSessionId == null) {
            throw new IllegalArgumentException("게임 세션 ID는 필수입니다.");
        }
        if (roundNo < 1) {
            throw new IllegalArgumentException("카드 제시 순서는 1 이상이어야 합니다.");
        }
        if (status == null) {
            throw new IllegalArgumentException("카드 결과 상태는 필수입니다.");
        }
        if (comparisonCount < 0 || wrongCount < 0 || wrongCount > comparisonCount) {
            throw new IllegalArgumentException("판정 횟수와 오답 횟수가 올바르지 않습니다.");
        }
        if (status == CardResultStatus.COMPLETED && (finalGrade == null || solveTimeMs == null)) {
            throw new IllegalArgumentException("완료 카드에는 최종 등급과 해결 시간이 필요합니다.");
        }
        if (status == CardResultStatus.INCOMPLETE && (finalGrade != null || solveTimeMs != null)) {
            throw new IllegalArgumentException("미완료 카드에는 최종 등급과 해결 시간을 기록하지 않습니다.");
        }
        if (solveTimeMs != null && solveTimeMs < 0) {
            throw new IllegalArgumentException("카드 해결 시간은 0 이상이어야 합니다.");
        }
    }
}
