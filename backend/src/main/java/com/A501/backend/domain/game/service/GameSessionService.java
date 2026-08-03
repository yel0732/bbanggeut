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
import com.A501.backend.domain.game.dto.request.SaveRecyclingResultRequest;
import com.A501.backend.domain.game.dto.request.SaveWordMatchProblemResultRequest;
import com.A501.backend.domain.game.dto.request.SaveWordMatchResultRequest;
import com.A501.backend.domain.game.dto.response.GameSessionResponse;
import com.A501.backend.domain.game.entity.CardRound;
import com.A501.backend.domain.game.entity.Game;
import com.A501.backend.domain.game.entity.GameSession;
import com.A501.backend.domain.game.entity.FruitNinjaFruitResult;
import com.A501.backend.domain.game.entity.FruitNinjaResult;
import com.A501.backend.domain.game.entity.RecyclingGameResult;
import com.A501.backend.domain.game.entity.WordMatchProblemResult;
import com.A501.backend.domain.game.entity.WordMatchProblemStatus;
import com.A501.backend.domain.game.entity.WordMatchResult;
import com.A501.backend.domain.game.exception.GameErrorCode;
import com.A501.backend.domain.game.exception.GameException;
import com.A501.backend.domain.game.metric.CardResultMetrics;
import com.A501.backend.domain.game.metric.DobbleResultCalculator;
import com.A501.backend.domain.game.metric.FruitResultCount;
import com.A501.backend.domain.game.metric.GameMetricType;
import com.A501.backend.domain.game.model.CompletedGameSessionSummary;
import com.A501.backend.domain.game.repository.GameRepository;
import com.A501.backend.domain.game.repository.GameSessionRepository;
import com.A501.backend.domain.game.repository.RecyclingGameResultRepository;
import com.A501.backend.domain.game.repository.WordMatchResultRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameSessionService {

    private static final String DOBBLE_GAME_CODE = "DOBBLE";
    private static final String FRUIT_NINJA_GAME_CODE = "FRUIT_NINJA";
    private static final String RECYCLING_GAME_CODE = "RECYCLING";
    private static final String WORD_MATCH_GAME_CODE = "WORD_MATCH";
    private static final int DOBBLE_MAX_DURATION_SECONDS = 90;
    private static final int FRUIT_NINJA_MAX_DURATION_SECONDS = 60;
    private static final String FRUIT_NINJA_RULES_VERSION = "fruit-ninja-v1";
    private static final Map<String, Integer> FRUIT_NINJA_SCORES = Map.ofEntries(
            Map.entry("GRAPE", 50), Map.entry("STRAWBERRY", 45),
            Map.entry("KIWI", 40), Map.entry("APPLE", 35),
            Map.entry("ORANGE", 30), Map.entry("BANANA", 28),
            Map.entry("MANGO", 25), Map.entry("COCONUT", 20),
            Map.entry("PINEAPPLE", 18), Map.entry("WATERMELON", 12));
    private static final int RECYCLING_MAX_DURATION_SECONDS = 60;
    private static final int RECYCLING_SCORE_PER_CORRECT = 100;
    private static final int WORD_MATCH_MAX_DURATION_SECONDS = 90;
    private static final int WORD_MATCH_SCORE_WITHOUT_HINT = 1_000;
    private static final int WORD_MATCH_SCORE_WITH_HINT = 700;
    private static final int WORD_MATCH_HINT_WRONG_THRESHOLD = 2;

    private final GameSessionRepository gameSessionRepository;
    private final GameRepository gameRepository;
    private final RecyclingGameResultRepository recyclingGameResultRepository;
    private final WordMatchResultRepository wordMatchResultRepository;
    private final GameSessionWriter gameSessionWriter;
    private final GameCurrentMemberProvider currentMemberProvider;
    private final GameChildAccessVerifier childAccessVerifier;

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
        Long parentId = currentMemberProvider.getCurrentMemberId();
        childAccessVerifier.verifyAccess(parentId, request.childId());
        validateTimeline(request);

        return gameSessionRepository.findByClientPlayId(request.clientPlayId())
                .map(existing -> responseForExisting(existing, request))
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
        if (request.gameType() == GameMetricType.RECYCLING) {
            return saveNewRecyclingSession(request);
        }
        if (request.gameType() == GameMetricType.WORD_MATCH) {
            return saveNewWordMatchSession(request);
        }
        if (gameRepository.findById(request.gameId())
                .map(Game::getGameCode)
                .filter(RECYCLING_GAME_CODE::equals)
                .isPresent()) {
            throw new GameException(GameErrorCode.GAME_TYPE_MISMATCH);
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

    private GameSessionResponse saveNewWordMatchSession(SaveGameResultRequest request) {
        SaveWordMatchResultRequest requestedResult = request.wordMatchResult();
        if (requestedResult == null
                || request.cardResults() != null
                || request.fruitNinjaResult() != null
                || request.recyclingResult() != null) {
            throw new GameException(GameErrorCode.INVALID_WORD_MATCH_RESULT);
        }

        Game game = gameRepository.findById(request.gameId())
                .orElseThrow(() -> new GameException(GameErrorCode.GAME_NOT_FOUND));
        if (!WORD_MATCH_GAME_CODE.equals(game.getGameCode())) {
            throw new GameException(GameErrorCode.GAME_TYPE_MISMATCH);
        }

        validateWordMatchResult(request, requestedResult);

        GameSession session = GameSession.create(
                request.childId(),
                request.gameId(),
                request.clientPlayId(),
                null,
                request.selectedAt(),
                request.playStartedAt(),
                request.completedAt(),
                request.playDurationSeconds(),
                request.score());
        WordMatchResult result = WordMatchResult.create(
                session.getGameSessionId(),
                requestedResult.rulesVersion(),
                requestedResult.solvedWordCount(),
                requestedResult.incompleteWordCount(),
                requestedResult.wrongCount(),
                requestedResult.hintUsedCount());
        List<WordMatchProblemResult> problemResults =
                requestedResult.problemResults().stream()
                        .map(item -> WordMatchProblemResult.create(
                                session.getGameSessionId(),
                                item.problemCode(),
                                item.questionOrder(),
                                item.wordLength(),
                                item.status(),
                                item.wrongCount(),
                                item.hintUsed(),
                                item.earnedScore(),
                                item.solveTimeMs()))
                        .toList();

        try {
            GameSession saved =
                    gameSessionWriter.saveWordMatchResult(session, result, problemResults);
            return GameSessionResponse.from(saved, result);
        } catch (DataIntegrityViolationException e) {
            return gameSessionRepository.findByClientPlayId(request.clientPlayId())
                    .map(existing -> responseForExisting(existing, request))
                    .orElseThrow(() -> e);
        }
    }

    private void validateWordMatchResult(
            SaveGameResultRequest request,
            SaveWordMatchResultRequest result) {
        if (request.playDurationSeconds() < 0
                || request.playDurationSeconds() > WORD_MATCH_MAX_DURATION_SECONDS) {
            throw new GameException(GameErrorCode.INVALID_WORD_MATCH_RESULT);
        }

        int solved = 0;
        int incomplete = 0;
        int wrong = 0;
        int hintUsed = 0;
        long score = 0;
        Set<Integer> orders = new HashSet<>();
        Set<String> problemCodes = new HashSet<>();
        int expectedOrder = 1;

        List<SaveWordMatchProblemResultRequest> ordered = result.problemResults().stream()
                .sorted(java.util.Comparator.comparingInt(
                        SaveWordMatchProblemResultRequest::questionOrder))
                .toList();
        for (SaveWordMatchProblemResultRequest item : ordered) {
            if (!orders.add(item.questionOrder()) || item.questionOrder() != expectedOrder++) {
                throw new GameException(GameErrorCode.INVALID_WORD_MATCH_RESULT);
            }
            if (!problemCodes.add(item.problemCode())
                    || item.wordLength() < 2
                    || item.wordLength() > 4) {
                throw new GameException(GameErrorCode.INVALID_WORD_MATCH_RESULT);
            }
            if (item.status() == WordMatchProblemStatus.COMPLETED) {
                solved++;
                if (item.solveTimeMs() == null || item.solveTimeMs() < 1) {
                    throw new GameException(GameErrorCode.INVALID_WORD_MATCH_RESULT);
                }
                int expectedScore = item.hintUsed()
                        ? WORD_MATCH_SCORE_WITH_HINT
                        : WORD_MATCH_SCORE_WITHOUT_HINT;
                if (item.earnedScore() != expectedScore) {
                    throw new GameException(GameErrorCode.INVALID_WORD_MATCH_RESULT);
                }
            } else {
                incomplete++;
                if (item.earnedScore() != 0 || item.solveTimeMs() != null
                        || item.questionOrder() != ordered.size()) {
                    throw new GameException(GameErrorCode.INVALID_WORD_MATCH_RESULT);
                }
            }
            if (item.wrongCount() < 0
                    || (item.hintUsed()
                            && item.wrongCount() < WORD_MATCH_HINT_WRONG_THRESHOLD)) {
                throw new GameException(GameErrorCode.INVALID_WORD_MATCH_RESULT);
            }
            wrong += item.wrongCount();
            hintUsed += item.hintUsed() ? 1 : 0;
            score += item.earnedScore();
        }

        if (incomplete > 1
                || solved != result.solvedWordCount()
                || incomplete != result.incompleteWordCount()
                || wrong != result.wrongCount()
                || hintUsed != result.hintUsedCount()
                || score != request.score()) {
            throw new GameException(GameErrorCode.INVALID_WORD_MATCH_RESULT);
        }
    }

    private GameSessionResponse saveNewRecyclingSession(SaveGameResultRequest request) {
        SaveRecyclingResultRequest requestedResult = request.recyclingResult();
        if (requestedResult == null
                || request.cardResults() != null
                || request.fruitNinjaResult() != null
                || request.wordMatchResult() != null) {
            throw new GameException(GameErrorCode.INVALID_RECYCLING_RESULT);
        }

        Game game = gameRepository.findById(request.gameId())
                .orElseThrow(() -> new GameException(GameErrorCode.GAME_NOT_FOUND));
        if (!RECYCLING_GAME_CODE.equals(game.getGameCode())) {
            throw new GameException(GameErrorCode.GAME_TYPE_MISMATCH);
        }

        validateRecyclingResult(request, requestedResult);

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
        RecyclingGameResult recyclingGameResult = RecyclingGameResult.create(
                gameSession.getGameSessionId(),
                requestedResult.rulesVersion(),
                requestedResult.successfulCount(),
                requestedResult.wrongCount());

        try {
            GameSession savedSession = gameSessionWriter.saveRecyclingResult(
                    gameSession,
                    recyclingGameResult);
            return GameSessionResponse.from(savedSession, recyclingGameResult);
        } catch (DataIntegrityViolationException e) {
            return gameSessionRepository.findByClientPlayId(request.clientPlayId())
                    .map(existing -> responseForExisting(existing, request))
                    .orElseThrow(() -> e);
        }
    }

    private void validateRecyclingResult(
            SaveGameResultRequest request,
            SaveRecyclingResultRequest requestedResult) {
        if (request.playDurationSeconds() < 0
                || request.playDurationSeconds() > RECYCLING_MAX_DURATION_SECONDS) {
            throw new GameException(GameErrorCode.INVALID_RECYCLING_RESULT);
        }
        if (requestedResult.successfulCount() < 0 || requestedResult.wrongCount() < 0) {
            throw new GameException(GameErrorCode.INVALID_RECYCLING_RESULT);
        }

        long expectedScore =
                (long) requestedResult.successfulCount() * RECYCLING_SCORE_PER_CORRECT;
        if (expectedScore > Integer.MAX_VALUE || request.score() != expectedScore) {
            throw new GameException(GameErrorCode.INVALID_RECYCLING_RESULT);
        }
    }

    private GameSessionResponse responseForExisting(
            GameSession existing,
            SaveGameResultRequest request) {
        if (!existing.getChildId().equals(request.childId())
                || !existing.getGameId().equals(request.gameId())) {
            throw new GameException(GameErrorCode.CLIENT_PLAY_ID_CONFLICT);
        }
        var recyclingResult =
                recyclingGameResultRepository.findById(existing.getGameSessionId());
        if (recyclingResult.isPresent()) {
            if (request.gameType() != GameMetricType.RECYCLING) {
                throw new GameException(GameErrorCode.GAME_TYPE_MISMATCH);
            }
            RecyclingGameResult result = recyclingResult.get();
            return GameSessionResponse.from(existing, result);
        }
        if (request.gameType() == GameMetricType.RECYCLING) {
            throw new GameException(GameErrorCode.INVALID_RECYCLING_RESULT);
        }
        var wordMatchResult = wordMatchResultRepository.findById(existing.getGameSessionId());
        if (wordMatchResult.isPresent()) {
            if (request.gameType() != GameMetricType.WORD_MATCH) {
                throw new GameException(GameErrorCode.GAME_TYPE_MISMATCH);
            }
            return GameSessionResponse.from(existing, wordMatchResult.get());
        }
        if (request.gameType() == GameMetricType.WORD_MATCH) {
            throw new GameException(GameErrorCode.INVALID_WORD_MATCH_RESULT);
        }
        return GameSessionResponse.from(existing);
    }

    private GameSessionResponse saveNewFruitNinjaSession(SaveGameResultRequest request) {
        SaveFruitNinjaResultRequest requestedResult = request.fruitNinjaResult();
        if (requestedResult == null
                || request.cardResults() != null
                || request.recyclingResult() != null
                || request.wordMatchResult() != null) {
            throw new GameException(GameErrorCode.INVALID_FRUIT_NINJA_RESULT);
        }

        validateGameCode(request.gameId(), FRUIT_NINJA_GAME_CODE);
        List<FruitResultCount> resultCounts = requestedResult.toMetrics();
        validateFruitNinjaResult(request, requestedResult, resultCounts);

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
        if (request.fruitNinjaResult() != null
                || request.recyclingResult() != null
                || request.wordMatchResult() != null) {
            throw new GameException(GameErrorCode.INVALID_DOBBLE_RESULT);
        }
        validateGameCode(request.gameId(), DOBBLE_GAME_CODE);
        validateDobbleRounds(request, requestedRounds);

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

    private void validateDobbleRounds(
            SaveGameResultRequest request,
            List<SaveDobbleCardResultRequest> requestedRounds) {
        if (request.playDurationSeconds() < 0
                || request.playDurationSeconds() > DOBBLE_MAX_DURATION_SECONDS) {
            throw new GameException(GameErrorCode.INVALID_DOBBLE_RESULT);
        }
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

        try {
            int expectedScore = DobbleResultCalculator.calculate(requestedRounds.stream()
                            .map(SaveDobbleCardResultRequest::toMetrics)
                            .toList())
                    .calculatedScore();
            if (request.score() != expectedScore) {
                throw new GameException(GameErrorCode.INVALID_DOBBLE_RESULT);
            }
        } catch (IllegalArgumentException e) {
            throw new GameException(GameErrorCode.INVALID_DOBBLE_RESULT);
        }
    }

    private void validateFruitNinjaResult(
            SaveGameResultRequest request,
            SaveFruitNinjaResultRequest requestedResult,
            List<FruitResultCount> fruitResults) {
        if (request.playDurationSeconds() < 0
                || request.playDurationSeconds() > FRUIT_NINJA_MAX_DURATION_SECONDS
                || !FRUIT_NINJA_RULES_VERSION.equals(requestedResult.rulesVersion())) {
            throw new GameException(GameErrorCode.INVALID_FRUIT_NINJA_RESULT);
        }
        try {
            validateFruitNinjaTotals(requestedResult, fruitResults);
        } catch (IllegalArgumentException e) {
            throw new GameException(GameErrorCode.INVALID_FRUIT_NINJA_RESULT);
        }

        long expectedScore = 0;
        for (FruitResultCount fruitResult : fruitResults) {
            Integer scorePerFruit = FRUIT_NINJA_SCORES.get(fruitResult.fruitCode());
            if (scorePerFruit == null) {
                throw new GameException(GameErrorCode.INVALID_FRUIT_NINJA_RESULT);
            }
            expectedScore += (long) fruitResult.slicedCount() * scorePerFruit;
        }
        if (expectedScore > Integer.MAX_VALUE || request.score() != expectedScore) {
            throw new GameException(GameErrorCode.INVALID_FRUIT_NINJA_RESULT);
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

    private void validateGameCode(Long gameId, String expectedGameCode) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new GameException(GameErrorCode.GAME_NOT_FOUND));
        if (!expectedGameCode.equals(game.getGameCode())) {
            throw new GameException(GameErrorCode.GAME_TYPE_MISMATCH);
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
