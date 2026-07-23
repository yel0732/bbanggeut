package com.A501.backend.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.A501.backend.config.JpaAuditingConfig;
import com.A501.backend.domain.game.entity.CardRound;
import com.A501.backend.domain.game.entity.GameSession;
import com.A501.backend.domain.game.metric.CardResultStatus;
import com.A501.backend.domain.game.metric.CognitiveGrade;
import com.A501.backend.domain.game.metric.DobbleResultMetrics;
import com.A501.backend.domain.game.repository.CardRoundRepository;
import com.A501.backend.domain.game.repository.GameSessionRepository;

@DataJpaTest
@Import({JpaAuditingConfig.class, GameReportQueryAdapter.class})
class GameReportQueryAdapterTest {

    private static final LocalDateTime STARTED_AT =
            LocalDateTime.of(2026, 7, 23, 0, 0);
    private static final LocalDateTime ENDED_AT =
            LocalDateTime.of(2026, 7, 24, 0, 0);

    @Autowired
    private GameSessionRepository gameSessionRepository;

    @Autowired
    private CardRoundRepository cardRoundRepository;

    @Autowired
    private GameReportQueryAdapter adapter;

    @BeforeEach
    void setUp() {
        cardRoundRepository.deleteAll();
        gameSessionRepository.deleteAll();
    }

    @Test
    void 지정한_아이와_완료_기간에_해당하는_도블_결과를_조회한다() {
        GameSession included = saveSession(15L, 7L, STARTED_AT, 120, 2550);
        saveRounds(included);

        GameSession endBoundary = saveSession(15L, 7L, ENDED_AT, 120, 1000);
        saveRounds(endBoundary);

        GameSession anotherChild = saveSession(16L, 7L, STARTED_AT.plusHours(1), 120, 1000);
        saveRounds(anotherChild);

        saveSession(15L, 8L, STARTED_AT.plusHours(2), 90, 500);

        var sources = adapter.findCompletedGames(15L, STARTED_AT, ENDED_AT);

        assertThat(sources).hasSize(1);
        var source = sources.getFirst();
        assertThat(source.gameSessionId()).isEqualTo(included.getGameSessionId());
        assertThat(source.gameId()).isEqualTo(7L);
        assertThat(source.gameCode()).isEqualTo("DOBBLE");
        assertThat(source.gameName()).isEqualTo("도블");
        assertThat(source.completedAt()).isEqualTo(STARTED_AT);
        assertThat(source.playDurationSeconds()).isEqualTo(120);
        assertThat(source.score()).isEqualTo(2550);

        assertThat(source.resultMetrics()).isInstanceOf(DobbleResultMetrics.class);
        var metrics = (DobbleResultMetrics) source.resultMetrics();
        assertThat(metrics.solvedCardCount()).isEqualTo(3);
        assertThat(metrics.perfectCount()).isEqualTo(1);
        assertThat(metrics.excellentCount()).isEqualTo(1);
        assertThat(metrics.goodCount()).isEqualTo(1);
        assertThat(metrics.incompleteCardCount()).isEqualTo(1);
        assertThat(metrics.cardResults())
                .extracting(cardResult -> cardResult.solveTimeMs())
                .containsExactly(1000L, 2000L, 3000L, null);
    }

    @Test
    void 완료_도블_세션_존재_여부를_같은_기간_조건으로_확인한다() {
        GameSession session = saveSession(15L, 7L, STARTED_AT.plusSeconds(1), 120, 1000);
        cardRoundRepository.saveAndFlush(completedRound(
                session.getGameSessionId(),
                1,
                1,
                0,
                CognitiveGrade.PERFECT,
                1000L));

        assertThat(adapter.existsCompletedGame(15L, STARTED_AT, ENDED_AT)).isTrue();
        assertThat(adapter.existsCompletedGame(16L, STARTED_AT, ENDED_AT)).isFalse();
    }

    @Test
    void 조회_기간이_올바르지_않으면_거부한다() {
        assertThatThrownBy(() ->
                adapter.findCompletedGames(15L, ENDED_AT, STARTED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private GameSession saveSession(
            Long childId,
            Long gameId,
            LocalDateTime completedAt,
            int playDurationSeconds,
            int score) {
        return gameSessionRepository.saveAndFlush(GameSession.create(
                childId,
                gameId,
                UUID.randomUUID(),
                null,
                completedAt.minusMinutes(3),
                completedAt.minusMinutes(2),
                completedAt,
                playDurationSeconds,
                score));
    }

    private void saveRounds(GameSession session) {
        cardRoundRepository.saveAllAndFlush(List.of(
                completedRound(
                        session.getGameSessionId(),
                        1,
                        1,
                        0,
                        CognitiveGrade.PERFECT,
                        1000L),
                completedRound(
                        session.getGameSessionId(),
                        2,
                        2,
                        1,
                        CognitiveGrade.EXCELLENT,
                        2000L),
                completedRound(
                        session.getGameSessionId(),
                        3,
                        5,
                        4,
                        CognitiveGrade.GOOD,
                        3000L),
                CardRound.create(
                        session.getGameSessionId(),
                        4,
                        CardResultStatus.INCOMPLETE,
                        1,
                        1,
                        null,
                        null)));
    }

    private CardRound completedRound(
            UUID gameSessionId,
            int roundNo,
            int comparisonCount,
            int wrongCount,
            CognitiveGrade grade,
            Long solveTimeMs) {
        return CardRound.create(
                gameSessionId,
                roundNo,
                CardResultStatus.COMPLETED,
                comparisonCount,
                wrongCount,
                grade,
                solveTimeMs);
    }
}
