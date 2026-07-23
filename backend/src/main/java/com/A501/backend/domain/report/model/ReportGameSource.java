package com.A501.backend.domain.report.model;

import java.time.LocalDateTime;
import java.util.UUID;

import com.A501.backend.domain.game.metric.GameResultMetrics;

public record ReportGameSource(
        UUID gameSessionId,
        Long gameId,
        String gameCode,
        String gameName,
        LocalDateTime completedAt,
        int playDurationSeconds,
        int score,
        GameResultMetrics resultMetrics
) {
}
