package com.A501.backend.domain.report.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import com.A501.backend.common.entity.BaseTimeEntity;
import com.A501.backend.domain.report.model.ReportGameSource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
        name = "report_game_result",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_report_game_result_report_session",
                        columnNames = {"report_id", "game_session_id"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportGameResult extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_game_result_id")
    private Long id;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "game_session_id", nullable = false)
    private UUID gameSessionId;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "game_code", nullable = false, length = 40)
    private String gameCode;

    @Column(name = "game_name", nullable = false, length = 100)
    private String gameName;

    @Column(name = "completed_at", nullable = false)
    private LocalDateTime completedAt;

    @Column(name = "play_duration_seconds", nullable = false)
    private int playDurationSeconds;

    @Column(name = "completed", nullable = false)
    private boolean completed;

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "result_metrics", columnDefinition = "TEXT")
    private String resultMetrics;

    @Builder(access = AccessLevel.PRIVATE)
    private ReportGameResult(
            Long reportId,
            UUID gameSessionId,
            Long gameId,
            String gameCode,
            String gameName,
            LocalDateTime completedAt,
            int playDurationSeconds,
            boolean completed,
            int score,
            String resultMetrics
    ) {
        this.reportId = reportId;
        this.gameSessionId = gameSessionId;
        this.gameId = gameId;
        this.gameCode = gameCode;
        this.gameName = gameName;
        this.completedAt = completedAt;
        this.playDurationSeconds = playDurationSeconds;
        this.completed = completed;
        this.score = score;
        this.resultMetrics = resultMetrics;
    }

    public static ReportGameResult snapshot(
            Long reportId,
            ReportGameSource source,
            String serializedResultMetrics
    ) {
        validate(reportId, source);

        return ReportGameResult.builder()
                .reportId(reportId)
                .gameSessionId(source.gameSessionId())
                .gameId(source.gameId())
                .gameCode(source.gameCode())
                .gameName(source.gameName())
                .completedAt(source.completedAt())
                .playDurationSeconds(source.playDurationSeconds())
                .completed(true)
                .score(source.score())
                .resultMetrics(serializedResultMetrics)
                .build();
    }

    private static void validate(
            Long reportId,
            ReportGameSource source
    ) {
        if (reportId == null) {
            throw new IllegalArgumentException("리포트 ID는 필수입니다.");
        }
        if (source == null) {
            throw new IllegalArgumentException("게임 결과 원본은 필수입니다.");
        }
        if (source.gameSessionId() == null || source.gameId() == null) {
            throw new IllegalArgumentException("게임 세션 ID와 게임 ID는 필수입니다.");
        }
        if (source.gameCode() == null || source.gameCode().isBlank()) {
            throw new IllegalArgumentException("게임 코드는 필수입니다.");
        }
        if (source.gameName() == null || source.gameName().isBlank()) {
            throw new IllegalArgumentException("게임명은 필수입니다.");
        }
        if (source.completedAt() == null) {
            throw new IllegalArgumentException("게임 완료 시각은 필수입니다.");
        }
        if (source.playDurationSeconds() < 0 || source.score() < 0) {
            throw new IllegalArgumentException("플레이 시간과 점수는 0 이상이어야 합니다.");
        }
    }
}
