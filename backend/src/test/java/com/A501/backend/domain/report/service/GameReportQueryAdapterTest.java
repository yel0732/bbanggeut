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
import com.A501.backend.domain.game.entity.FruitNinjaResult;
import com.A501.backend.domain.game.entity.RecyclingGameResult;
import com.A501.backend.domain.game.entity.WordMatchResult;
import com.A501.backend.domain.game.metric.CardResultStatus;
import com.A501.backend.domain.game.metric.CognitiveGrade;
import com.A501.backend.domain.game.metric.DobbleResultMetrics;
import com.A501.backend.domain.game.metric.FruitNinjaResultMetrics;
import com.A501.backend.domain.game.metric.RecyclingResultMetrics;
import com.A501.backend.domain.game.metric.WordMatchResultMetrics;
import com.A501.backend.domain.game.repository.CardRoundRepository;
import com.A501.backend.domain.game.repository.GameSessionRepository;
import com.A501.backend.domain.game.repository.FruitNinjaResultRepository;
import com.A501.backend.domain.game.repository.RecyclingGameResultRepository;
import com.A501.backend.domain.game.repository.WordMatchResultRepository;

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
    private FruitNinjaResultRepository fruitNinjaResultRepository;

    @Autowired
    private RecyclingGameResultRepository recyclingGameResultRepository;

    @Autowired
    private WordMatchResultRepository wordMatchResultRepository;

    @Autowired
    private GameReportQueryAdapter adapter;

    @BeforeEach
    void setUp() {
        cardRoundRepository.deleteAll();
        fruitNinjaResultRepository.deleteAll();
        recyclingGameResultRepository.deleteAll();
        wordMatchResultRepository.deleteAll();
        gameSessionRepository.deleteAll();
    }

    @Test
    void returnsDobbleFruitNinjaAndRecyclingResultsThroughTheSameQuery() {
        GameSession dobble = saveSession(
                15L, 7L, STARTED_AT.plusMinutes(1), 120, 2550);
        saveRounds(dobble);
        GameSession fruitNinja = saveSession(
                15L, 8L, STARTED_AT.plusMinutes(2), 120, 800);
        fruitNinjaResultRepository.saveAndFlush(FruitNinjaResult.create(
                fruitNinja.getGameSessionId(), "fruit-v1", 10, 7));
        GameSession recycling = saveSession(
                15L, 9L, STARTED_AT.plusMinutes(3), 60, 1200);
        recyclingGameResultRepository.saveAndFlush(RecyclingGameResult.create(
                recycling.getGameSessionId(), "RECYCLING_V1", 12, 3));
        GameSession wordMatch = saveSession(
                15L, 10L, STARTED_AT.plusMinutes(4), 90, 1700);
        wordMatchResultRepository.saveAndFlush(WordMatchResult.create(
                wordMatch.getGameSessionId(), "WORD_MATCH_V1", 2, 1, 3, 1));

        var sources = adapter.findCompletedGames(15L, STARTED_AT, ENDED_AT);

        assertThat(sources).hasSize(4);
        assertThat(sources).extracting("gameCode")
                .containsExactly("DOBBLE", "FRUIT_NINJA", "RECYCLING", "WORD_MATCH");

        var fruitSource = sources.get(1);
        assertThat(fruitSource.gameSessionId()).isEqualTo(fruitNinja.getGameSessionId());
        assertThat(fruitSource.gameName()).isEqualTo("과일 자르기");
        assertThat(fruitSource.playDurationSeconds()).isEqualTo(120);
        assertThat(fruitSource.score()).isEqualTo(800);
        assertThat(fruitSource.resultMetrics()).isInstanceOf(FruitNinjaResultMetrics.class);
        var metrics = (FruitNinjaResultMetrics) fruitSource.resultMetrics();
        assertThat(metrics.totalFruitCount()).isEqualTo(10);
        assertThat(metrics.slicedFruitCount()).isEqualTo(7);
        assertThat(metrics.rulesVersion()).isEqualTo("fruit-v1");

        var recyclingSource = sources.get(2);
        assertThat(recyclingSource.gameSessionId()).isEqualTo(recycling.getGameSessionId());
        assertThat(recyclingSource.gameName()).isEqualTo("분리수거");
        assertThat(recyclingSource.playDurationSeconds()).isEqualTo(60);
        assertThat(recyclingSource.score()).isEqualTo(1200);
        assertThat(recyclingSource.resultMetrics()).isInstanceOf(RecyclingResultMetrics.class);
        var recyclingMetrics = (RecyclingResultMetrics) recyclingSource.resultMetrics();
        assertThat(recyclingMetrics.successfulCount()).isEqualTo(12);
        assertThat(recyclingMetrics.wrongCount()).isEqualTo(3);
        assertThat(recyclingMetrics.rulesVersion()).isEqualTo("RECYCLING_V1");

        var wordSource = sources.get(3);
        assertThat(wordSource.gameName()).isEqualTo("낱말 맞추기");
        assertThat(wordSource.resultMetrics()).isInstanceOf(WordMatchResultMetrics.class);
        var wordMetrics = (WordMatchResultMetrics) wordSource.resultMetrics();
        assertThat(wordMetrics.solvedWordCount()).isEqualTo(2);
        assertThat(wordMetrics.incompleteWordCount()).isEqualTo(1);
        assertThat(wordMetrics.wrongCount()).isEqualTo(3);
        assertThat(wordMetrics.hintUsedCount()).isEqualTo(1);
    }

    @Test
    void excludesFruitNinjaResultAtEndBoundaryAndForAnotherChild() {
        GameSession included = saveSession(
                15L, 8L, STARTED_AT, 120, 800);
        fruitNinjaResultRepository.saveAndFlush(FruitNinjaResult.create(
                included.getGameSessionId(), "fruit-v1", 10, 7));
        GameSession endBoundary = saveSession(
                15L, 8L, ENDED_AT, 120, 500);
        fruitNinjaResultRepository.saveAndFlush(FruitNinjaResult.create(
                endBoundary.getGameSessionId(), "fruit-v1", 8, 5));
        GameSession anotherChild = saveSession(
                16L, 8L, STARTED_AT.plusHours(1), 120, 300);
        fruitNinjaResultRepository.saveAndFlush(FruitNinjaResult.create(
                anotherChild.getGameSessionId(), "fruit-v1", 5, 3));

        var sources = adapter.findCompletedGames(15L, STARTED_AT, ENDED_AT);

        assertThat(sources).singleElement()
                .extracting("gameSessionId")
                .isEqualTo(included.getGameSessionId());
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
        assertThat(source.gameName()).isEqualTo("그림 맞추기");
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
