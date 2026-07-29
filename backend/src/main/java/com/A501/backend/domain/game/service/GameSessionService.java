package com.A501.backend.domain.game.service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.game.dto.request.SaveDobbleCardResultRequest;
import com.A501.backend.domain.game.dto.request.SaveGameResultRequest;
import com.A501.backend.domain.game.dto.request.SaveFruitNinjaResultRequest;
import com.A501.backend.domain.game.dto.response.GameSessionResponse;
import com.A501.backend.domain.game.entity.CardRound;
import com.A501.backend.domain.game.entity.Game;
import com.A501.backend.domain.game.entity.GameSession;
import com.A501.backend.domain.game.entity.FruitNinjaFruitResult;
import com.A501.backend.domain.game.entity.FruitNinjaResult;
import com.A501.backend.domain.game.metric.CardResultMetrics;
import com.A501.backend.domain.game.metric.FruitResultCount;
import com.A501.backend.domain.game.metric.GameMetricType;
import com.A501.backend.domain.game.model.CompletedGameSessionSummary;
import com.A501.backend.domain.game.repository.GameRepository;
import com.A501.backend.domain.game.repository.GameSessionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameSessionService {

    private final GameSessionRepository gameSessionRepository;
    private final GameRepository gameRepository;
    private final GameSessionWriter gameSessionWriter;

    public boolean existsForChild(UUID gameSessionId, Long childId) {
        return gameSessionRepository.findById(gameSessionId)
                .filter(gameSession -> gameSession.getChildId().equals(childId))
                .isPresent();
    }

    public List<CompletedGameSessionSummary> findCompletedSessionsForChild(
            Long childId,
            LocalDateTime startedAt,
            LocalDateTime endedAt) {
        if (childId == null || startedAt == null || endedAt == null
                || !startedAt.isBefore(endedAt)) {
            throw new IllegalArgumentException("게임 세션 조회 범위가 올바르지 않습니다.");
        }
        List<GameSession> sessions = gameSessionRepository
                .findAllByChildIdAndCompletedAtGreaterThanEqualAndCompletedAtLessThanOrderByCompletedAtAsc(
                        childId,
                        startedAt,
                        endedAt);
        Map<Long, String> gameNames = findGameNames(sessions);
        return sessions.stream()
                .map(session -> CompletedGameSessionSummary.from(
                        session,
                        gameNames.get(session.getGameId())))
                .toList();
    }

    private Map<Long, String> findGameNames(List<GameSession> sessions) {
        Set<Long> gameIds = sessions.stream()
                .map(GameSession::getGameId)
                .collect(Collectors.toSet());
        if (gameIds.isEmpty()) {
            return Map.of();
        }
        return gameRepository.findAllByGameIdIn(gameIds).stream()
                .collect(Collectors.toMap(Game::getGameId, Game::getGameName));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public GameSessionResponse saveCompletedResult(SaveGameResultRequest request) {
        validateTimeline(request);

        return gameSessionRepository.findByClientPlayId(request.clientPlayId())
                .map(GameSessionResponse::from)
                .orElseGet(() -> saveNewSession(request));
    }

    private GameSessionResponse saveNewSession(SaveGameResultRequest request) {
        validateScore(request.score());

        if (request.gameType() == GameMetricType.DOBBLE) {
            return saveNewDobbleSession(request);
        }
        if (request.gameType() == GameMetricType.FRUIT_NINJA) {
            return saveNewFruitNinjaSession(request);
        }
        GameSession gameSession = GameSession.create(
                request.childId(),
                request.gameId(),
                request.clientPlayId(),
                request.difficultyCode(),
                request.selectedAt(),
                request.playStartedAt(),
                request.completedAt(),
                request.playDurationSeconds(),
                request.score());

        try {
            return GameSessionResponse.from(gameSessionWriter.saveAndFlush(gameSession));
        } catch (DataIntegrityViolationException e) {
            return gameSessionRepository.findByClientPlayId(request.clientPlayId())
                    .map(GameSessionResponse::from)
                    .orElseThrow(() -> e);
        }
    }

    private GameSessionResponse saveNewFruitNinjaSession(SaveGameResultRequest request) {
        SaveFruitNinjaResultRequest requestedResult = request.fruitNinjaResult();
        if (requestedResult == null) {
            throw new IllegalArgumentException("후르츠 게임 상세 결과는 필수입니다.");
        }

        List<FruitResultCount> resultCounts = requestedResult.toMetrics();
        validateFruitNinjaTotals(requestedResult, resultCounts);

        GameSession gameSession = GameSession.create(
                request.childId(),
                request.gameId(),
                request.clientPlayId(),
                null,
                request.selectedAt(),
                request.playStartedAt(),
                request.completedAt(),
                request.playDurationSeconds(),
                request.score());
        FruitNinjaResult fruitNinjaResult = FruitNinjaResult.create(
                gameSession.getGameSessionId(),
                requestedResult.rulesVersion(),
                requestedResult.totalFruitCount(),
                requestedResult.slicedFruitCount());
        List<FruitNinjaFruitResult> fruitResults = resultCounts.stream()
                .map(result -> FruitNinjaFruitResult.create(
                        gameSession.getGameSessionId(), result))
                .toList();

        try {
            return GameSessionResponse.from(gameSessionWriter.saveFruitNinjaResult(
                    gameSession, fruitNinjaResult, fruitResults));
        } catch (DataIntegrityViolationException e) {
            return gameSessionRepository.findByClientPlayId(request.clientPlayId())
                    .map(GameSessionResponse::from)
                    .orElseThrow(() -> e);
        }
    }

    private GameSessionResponse saveNewDobbleSession(SaveGameResultRequest request) {
        List<SaveDobbleCardResultRequest> requestedRounds = request.cardResults();
        validateDobbleRounds(requestedRounds);

        List<CardResultMetrics> cardResults = requestedRounds.stream()
                .map(SaveDobbleCardResultRequest::toMetrics)
                .toList();

        GameSession gameSession = GameSession.create(
                request.childId(),
                request.gameId(),
                request.clientPlayId(),
                null,
                request.selectedAt(),
                request.playStartedAt(),
                request.completedAt(),
                request.playDurationSeconds(),
                request.score());

        List<CardRound> cardRounds = createCardRounds(
                gameSession.getGameSessionId(),
                requestedRounds,
                cardResults);

        try {
            return GameSessionResponse.from(
                    gameSessionWriter.saveDobbleResult(gameSession, cardRounds));
        } catch (DataIntegrityViolationException e) {
            return gameSessionRepository.findByClientPlayId(request.clientPlayId())
                    .map(GameSessionResponse::from)
                    .orElseThrow(() -> e);
        }
    }

    private void validateDobbleRounds(List<SaveDobbleCardResultRequest> requestedRounds) {
        if (requestedRounds == null || requestedRounds.isEmpty()) {
            throw new IllegalArgumentException("도블 카드별 결과는 한 건 이상 필요합니다.");
        }

        Set<Integer> roundNumbers = new HashSet<>();
        for (SaveDobbleCardResultRequest round : requestedRounds) {
            if (round == null) {
                throw new IllegalArgumentException("도블 카드 결과에는 null이 포함될 수 없습니다.");
            }
            if (!roundNumbers.add(round.roundNo())) {
                throw new IllegalArgumentException("카드 제시 순서는 중복될 수 없습니다: " + round.roundNo());
            }
        }
    }

    private void validateFruitNinjaTotals(
            SaveFruitNinjaResultRequest requestedResult,
            List<FruitResultCount> fruitResults) {
        if (requestedResult.slicedFruitCount() > requestedResult.totalFruitCount()) {
            throw new IllegalArgumentException("자른 과일 수는 생성된 과일 수를 초과할 수 없습니다.");
        }

        Set<String> fruitCodes = new HashSet<>();
        long generatedCount = 0;
        long slicedCount = 0;
        for (FruitResultCount fruitResult : fruitResults) {
            if (!fruitCodes.add(fruitResult.fruitCode())) {
                throw new IllegalArgumentException(
                        "과일별 결과에는 같은 과일 코드가 중복될 수 없습니다: "
                                + fruitResult.fruitCode());
            }
            generatedCount += fruitResult.generatedCount();
            slicedCount += fruitResult.slicedCount();
        }

        if (generatedCount != requestedResult.totalFruitCount()
                || slicedCount != requestedResult.slicedFruitCount()) {
            throw new IllegalArgumentException("과일별 집계와 전체 과일 집계가 일치하지 않습니다.");
        }
    }

    private void validateScore(Integer score) {
        if (score == null || score < 0) {
            throw new IllegalArgumentException("완료된 게임 결과에는 0 이상의 점수가 필요합니다.");
        }
    }

    private List<CardRound> createCardRounds(
            UUID gameSessionId,
            List<SaveDobbleCardResultRequest> requestedRounds,
            List<CardResultMetrics> cardResults) {
        return java.util.stream.IntStream.range(0, requestedRounds.size())
                .mapToObj(index -> {
                    SaveDobbleCardResultRequest request = requestedRounds.get(index);
                    CardResultMetrics metrics = cardResults.get(index);
                    return CardRound.create(
                            gameSessionId,
                            request.roundNo(),
                            metrics.status(),
                            metrics.comparisonCount(),
                            metrics.wrongCount(),
                            metrics.finalGrade(),
                            metrics.solveTimeMs());
                })
                .toList();
    }

    private void validateTimeline(SaveGameResultRequest request) {
        if (request.playStartedAt().isBefore(request.selectedAt())) {
            throw new IllegalArgumentException("실제 게임 시작 시각은 게임 선택 시각보다 빠를 수 없습니다.");
        }
        if (request.completedAt().isBefore(request.playStartedAt())) {
            throw new IllegalArgumentException("게임 완료 시각은 실제 게임 시작 시각보다 빠를 수 없습니다.");
        }
    }
}
