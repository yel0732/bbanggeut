package com.A501.backend.domain.report.port;

import java.time.LocalDateTime;
import java.util.List;

import com.A501.backend.domain.report.model.ReportGameSource;

public interface GameReportQueryPort {

    boolean existsCompletedGame(
            Long childId,
            LocalDateTime startedAt,
            LocalDateTime endedAt);

    List<ReportGameSource> findCompletedGames(
            Long childId,
            LocalDateTime startedAt,
            LocalDateTime endedAt);
}
