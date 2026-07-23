package com.A501.backend.domain.report.port;

import java.time.LocalDateTime;

public interface GameReportQueryPort {

    boolean existsCompletedGame(
            Long childId,
            LocalDateTime startedAt,
            LocalDateTime endedAt);
}
