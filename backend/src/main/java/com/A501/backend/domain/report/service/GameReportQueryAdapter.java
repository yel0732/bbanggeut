package com.A501.backend.domain.report.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.game.entity.CardRound;
import com.A501.backend.domain.game.entity.FruitNinjaResult;
import com.A501.backend.domain.game.entity.GameSession;
import com.A501.backend.domain.game.metric.CardResultMetrics;
import com.A501.backend.domain.game.metric.DobbleResultCalculator;
import com.A501.backend.domain.game.metric.FruitNinjaResultMetrics;
import com.A501.backend.domain.game.repository.CardRoundRepository;
import com.A501.backend.domain.game.repository.FruitNinjaResultRepository;
import com.A501.backend.domain.game.repository.GameSessionRepository;
import com.A501.backend.domain.report.model.ReportGameSource;
import com.A501.backend.domain.report.port.GameReportQueryPort;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameReportQueryAdapter implements GameReportQueryPort {

    private static final String DOBBLE_GAME_CODE = "DOBBLE";
    private static final String DOBBLE_GAME_NAME = "도블";
    private static final String FRUIT_NINJA_GAME_CODE = "FRUIT_NINJA";
    private static final String FRUIT_NINJA_GAME_NAME = "후르츠 닌자";

    private final GameSessionRepository gameSessionRepository;
    private final CardRoundRepository cardRoundRepository;
    private final FruitNinjaResultRepository fruitNinjaResultRepository;

    @Override
    public boolean existsCompletedGame(
            Long childId,
            LocalDateTime startedAt,
            LocalDateTime endedAt) {
        return !findCompletedGames(childId, startedAt, endedAt).isEmpty();
    }

    @Override
    public List<ReportGameSource> findCompletedGames(
            Long childId,
            LocalDateTime startedAt,
            LocalDateTime endedAt) {
        validatePeriod(childId, startedAt, endedAt);

        List<GameSession> sessions =
                gameSessionRepository
                        .findAllByChildIdAndCompletedAtGreaterThanEqualAndCompletedAtLessThanOrderByCompletedAtAsc(
                                childId, startedAt, endedAt);
        if (sessions.isEmpty()) {
            return List.of();
        }

        List<UUID> sessionIds = sessions.stream()
                .map(GameSession::getGameSessionId)
                .toList();
        Map<UUID, List<CardRound>> roundsBySessionId =
                cardRoundRepository
                        .findAllByGameSessionIdInOrderByGameSessionIdAscRoundNoAsc(sessionIds)
                        .stream()
                        .collect(Collectors.groupingBy(CardRound::getGameSessionId));
        Map<UUID, FruitNinjaResult> fruitResultsBySessionId =
                fruitNinjaResultRepository.findAllById(sessionIds)
                        .stream()
                        .collect(Collectors.toMap(
                                FruitNinjaResult::getGameSessionId,
                                result -> result));

        return sessions.stream()
                .map(session -> toReportSource(
                        session,
                        roundsBySessionId.get(session.getGameSessionId()),
                        fruitResultsBySessionId.get(session.getGameSessionId())))
                .filter(Objects::nonNull)
                .toList();
    }

    private ReportGameSource toReportSource(
            GameSession session,
            List<CardRound> rounds,
            FruitNinjaResult fruitResult) {
        if (rounds != null) {
            return toDobbleReportSource(session, rounds);
        }
        if (fruitResult != null) {
            return toFruitNinjaReportSource(session, fruitResult);
        }
        return null;
    }

    private ReportGameSource toDobbleReportSource(
            GameSession session,
            List<CardRound> rounds) {
        List<CardResultMetrics> cardResults = rounds.stream()
                .map(this::toMetrics)
                .toList();
        return new ReportGameSource(
                session.getGameSessionId(),
                session.getGameId(),
                DOBBLE_GAME_CODE,
                DOBBLE_GAME_NAME,
                session.getCompletedAt(),
                session.getPlayDurationSeconds(),
                session.getScore(),
                DobbleResultCalculator.calculate(cardResults));
    }

    private ReportGameSource toFruitNinjaReportSource(
            GameSession session,
            FruitNinjaResult result) {
        return new ReportGameSource(
                session.getGameSessionId(),
                session.getGameId(),
                FRUIT_NINJA_GAME_CODE,
                FRUIT_NINJA_GAME_NAME,
                session.getCompletedAt(),
                session.getPlayDurationSeconds(),
                session.getScore(),
                new FruitNinjaResultMetrics(
                        result.getTotalFruitCount(),
                        result.getSlicedFruitCount(),
                        result.getRulesVersion()));
    }

    private CardResultMetrics toMetrics(CardRound round) {
        return new CardResultMetrics(
                "round-" + round.getRoundNo(),
                round.getComparisonCount(),
                round.getWrongCount(),
                round.getStatus(),
                round.getFinalGrade(),
                false,
                round.getSolveTimeMs());
    }

    private void validatePeriod(
            Long childId,
            LocalDateTime startedAt,
            LocalDateTime endedAt) {
        if (childId == null || childId < 1) {
            throw new IllegalArgumentException("아이 ID는 1 이상이어야 합니다.");
        }
        if (startedAt == null || endedAt == null || !startedAt.isBefore(endedAt)) {
            throw new IllegalArgumentException("조회 시작 시각은 종료 시각보다 빨라야 합니다.");
        }
    }
}
