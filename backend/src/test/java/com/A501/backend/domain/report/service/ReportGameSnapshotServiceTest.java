package com.A501.backend.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.A501.backend.config.JpaAuditingConfig;
import com.A501.backend.domain.game.metric.DobbleResultMetrics;
import com.A501.backend.domain.game.metric.FruitNinjaResultMetrics;
import com.A501.backend.domain.game.metric.GameResultMetrics;
import com.A501.backend.domain.report.entity.DailyReport;
import com.A501.backend.domain.report.model.ReportGameSource;
import com.A501.backend.domain.report.model.ReportPeriod;
import com.A501.backend.domain.report.port.GameReportQueryPort;
import com.A501.backend.domain.report.repository.DailyReportRepository;
import com.A501.backend.domain.report.repository.ReportGameResultRepository;

import tools.jackson.databind.ObjectMapper;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class ReportGameSnapshotServiceTest {

    @Autowired
    private DailyReportRepository dailyReportRepository;

    @Autowired
    private ReportGameResultRepository reportGameResultRepository;

    private FakeGameSourceQueryPort gameSourceQueryPort;
    private ReportGameSnapshotService snapshotService;
    private DailyReport dailyReport;

    @BeforeEach
    void setUp() {
        gameSourceQueryPort = new FakeGameSourceQueryPort();
        snapshotService = new ReportGameSnapshotService(
                dailyReportRepository,
                reportGameResultRepository,
                gameSourceQueryPort,
                new ReportGameResultMapper(new ObjectMapper()));

        ReportPeriod period = new ReportPeriod(
                LocalDateTime.of(2026, 7, 22, 19, 0),
                LocalDateTime.of(2026, 7, 23, 19, 0),
                LocalDate.of(2026, 7, 23));
        dailyReport = dailyReportRepository.saveAndFlush(
                DailyReport.pending(15L, period));
    }

    @Test
    void 완료된_게임이_없으면_스냅샷을_저장하지_않는다() {
        gameSourceQueryPort.returns(List.of());

        int createdCount = snapshotService.createSnapshots(
                dailyReport.getId());

        assertThat(createdCount).isZero();
        assertThat(reportGameResultRepository.findAll()).isEmpty();
    }

    @Test
    void 완료된_게임_결과를_리포트_스냅샷으로_저장한다() {
        UUID dobbleSessionId = UUID.randomUUID();
        UUID fruitSessionId = UUID.randomUUID();
        gameSourceQueryPort.returns(List.of(
                source(
                        dobbleSessionId,
                        1L,
                        "DOBBLE",
                        "도블",
                        LocalDateTime.of(2026, 7, 23, 10, 0),
                        180,
                        3500,
                        new DobbleResultMetrics(
                                0, 0, 0, 0, 0, 0, List.of())),
                source(
                        fruitSessionId,
                        2L,
                        "FRUIT_NINJA",
                        "과일 닌자",
                        LocalDateTime.of(2026, 7, 23, 11, 0),
                        90,
                        1200,
                        new FruitNinjaResultMetrics(15, 12, "v1"))));

        int createdCount = snapshotService.createSnapshots(
                dailyReport.getId());

        var snapshots = reportGameResultRepository.findAllByReportId(
                dailyReport.getId());

        assertThat(createdCount).isEqualTo(2);
        assertThat(snapshots).hasSize(2);
        assertThat(snapshots)
                .extracting(result -> result.getGameSessionId())
                .containsExactlyInAnyOrder(dobbleSessionId, fruitSessionId);
        assertThat(snapshots)
                .allMatch(result -> result.isCompleted());

        var dobbleSnapshot = snapshots.stream()
                .filter(result ->
                        result.getGameSessionId().equals(dobbleSessionId))
                .findFirst()
                .orElseThrow();

        assertThat(dobbleSnapshot.getGameName()).isEqualTo("도블");
        assertThat(dobbleSnapshot.getGameCode()).isEqualTo("DOBBLE");
        assertThat(dobbleSnapshot.getPlayDurationSeconds()).isEqualTo(180);
        assertThat(dobbleSnapshot.getScore()).isEqualTo(3500);
        assertThat(dobbleSnapshot.getResultMetrics())
                .contains("\"solvedCardCount\":0");

        var fruitSnapshot = snapshots.stream()
                .filter(result ->
                        result.getGameSessionId().equals(fruitSessionId))
                .findFirst()
                .orElseThrow();

        assertThat(fruitSnapshot.getGameCode()).isEqualTo("FRUIT_NINJA");
        assertThat(fruitSnapshot.getResultMetrics())
                .contains("\"totalFruitCount\":15")
                .contains("\"slicedFruitCount\":12");
    }

    @Test
    void 같은_게임_세션을_다시_처리해도_중복_저장하지_않는다() {
        UUID gameSessionId = UUID.randomUUID();
        gameSourceQueryPort.returns(List.of(
                source(
                        gameSessionId,
                        1L,
                        "DOBBLE",
                        "도블",
                        LocalDateTime.of(2026, 7, 23, 10, 0),
                        180,
                        3500,
                        null)));

        int firstCount = snapshotService.createSnapshots(
                dailyReport.getId());
        int secondCount = snapshotService.createSnapshots(
                dailyReport.getId());

        assertThat(firstCount).isEqualTo(1);
        assertThat(secondCount).isZero();
        assertThat(reportGameResultRepository.findAllByReportId(
                dailyReport.getId())).hasSize(1);
    }

    @Test
    void 원본_응답이_변경되어도_기존_스냅샷은_변경하지_않는다() {
        UUID gameSessionId = UUID.randomUUID();
        gameSourceQueryPort.returns(List.of(
                source(
                        gameSessionId,
                        1L,
                        "DOBBLE",
                        "도블",
                        LocalDateTime.of(2026, 7, 23, 10, 0),
                        180,
                        3500,
                        null)));
        snapshotService.createSnapshots(dailyReport.getId());

        gameSourceQueryPort.returns(List.of(
                source(
                        gameSessionId,
                        1L,
                        "DOBBLE",
                        "변경된 이름",
                        LocalDateTime.of(2026, 7, 23, 10, 0),
                        200,
                        9999,
                        new DobbleResultMetrics(
                                0, 0, 0, 0, 0, 0, List.of()))));
        snapshotService.createSnapshots(dailyReport.getId());

        var snapshot = reportGameResultRepository
                .findAllByReportId(dailyReport.getId())
                .getFirst();

        assertThat(snapshot.getGameName()).isEqualTo("도블");
        assertThat(snapshot.getPlayDurationSeconds()).isEqualTo(180);
        assertThat(snapshot.getScore()).isEqualTo(3500);
        assertThat(snapshot.getResultMetrics()).isNull();
    }

    private ReportGameSource source(
            UUID gameSessionId,
            Long gameId,
            String gameCode,
            String gameName,
            LocalDateTime completedAt,
            int playDurationSeconds,
            int score,
            GameResultMetrics resultMetrics
    ) {
        return new ReportGameSource(
                gameSessionId,
                gameId,
                gameCode,
                gameName,
                completedAt,
                playDurationSeconds,
                score,
                resultMetrics);
    }

    private static class FakeGameSourceQueryPort
            implements GameReportQueryPort {

        private List<ReportGameSource> sources = List.of();

        void returns(List<ReportGameSource> sources) {
            this.sources = List.copyOf(sources);
        }

        @Override
        public boolean existsCompletedGame(
                Long childId,
                LocalDateTime startedAt,
                LocalDateTime endedAt
        ) {
            return !sources.isEmpty();
        }

        @Override
        public List<ReportGameSource> findCompletedGames(
                Long childId,
                LocalDateTime startedAt,
                LocalDateTime endedAt
        ) {
            return sources;
        }
    }
}
