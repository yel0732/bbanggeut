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
@Table(name = "word_match_result")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WordMatchResult {

    @Id
    @Column(name = "game_session_id", nullable = false, updatable = false)
    private UUID gameSessionId;

    @Column(name = "rules_version", nullable = false, updatable = false, length = 40)
    private String rulesVersion;

    @Column(name = "solved_word_count", nullable = false)
    private int solvedWordCount;

    @Column(name = "incomplete_word_count", nullable = false)
    private int incompleteWordCount;

    @Column(name = "wrong_count", nullable = false)
    private int wrongCount;

    @Column(name = "hint_used_count", nullable = false)
    private int hintUsedCount;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private WordMatchResult(
            UUID gameSessionId,
            String rulesVersion,
            int solvedWordCount,
            int incompleteWordCount,
            int wrongCount,
            int hintUsedCount) {
        this.gameSessionId = gameSessionId;
        this.rulesVersion = rulesVersion;
        this.solvedWordCount = solvedWordCount;
        this.incompleteWordCount = incompleteWordCount;
        this.wrongCount = wrongCount;
        this.hintUsedCount = hintUsedCount;
    }

    public static WordMatchResult create(
            UUID gameSessionId,
            String rulesVersion,
            int solvedWordCount,
            int incompleteWordCount,
            int wrongCount,
            int hintUsedCount) {
        return new WordMatchResult(
                gameSessionId,
                rulesVersion,
                solvedWordCount,
                incompleteWordCount,
                wrongCount,
                hintUsedCount);
    }
}
