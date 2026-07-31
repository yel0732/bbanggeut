package com.A501.backend.domain.game.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "word_match_problem_result")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WordMatchProblemResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "word_match_problem_result_id")
    private Long wordMatchProblemResultId;

    @Column(name = "game_session_id", nullable = false, updatable = false)
    private UUID gameSessionId;

    @Column(name = "problem_code", nullable = false, updatable = false, length = 40)
    private String problemCode;

    @Column(name = "question_order", nullable = false, updatable = false)
    private int questionOrder;

    @Column(name = "word_length", nullable = false, updatable = false)
    private int wordLength;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, updatable = false, length = 20)
    private WordMatchProblemStatus status;

    @Column(name = "wrong_count", nullable = false, updatable = false)
    private int wrongCount;

    @Column(name = "hint_used", nullable = false, updatable = false)
    private boolean hintUsed;

    @Column(name = "earned_score", nullable = false, updatable = false)
    private int earnedScore;

    @Column(name = "solve_time_ms", updatable = false)
    private Long solveTimeMs;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private WordMatchProblemResult(
            UUID gameSessionId,
            String problemCode,
            int questionOrder,
            int wordLength,
            WordMatchProblemStatus status,
            int wrongCount,
            boolean hintUsed,
            int earnedScore,
            Long solveTimeMs) {
        this.gameSessionId = gameSessionId;
        this.problemCode = problemCode;
        this.questionOrder = questionOrder;
        this.wordLength = wordLength;
        this.status = status;
        this.wrongCount = wrongCount;
        this.hintUsed = hintUsed;
        this.earnedScore = earnedScore;
        this.solveTimeMs = solveTimeMs;
    }

    public static WordMatchProblemResult create(
            UUID gameSessionId,
            String problemCode,
            int questionOrder,
            int wordLength,
            WordMatchProblemStatus status,
            int wrongCount,
            boolean hintUsed,
            int earnedScore,
            Long solveTimeMs) {
        return new WordMatchProblemResult(
                gameSessionId,
                problemCode,
                questionOrder,
                wordLength,
                status,
                wrongCount,
                hintUsed,
                earnedScore,
                solveTimeMs);
    }
}
