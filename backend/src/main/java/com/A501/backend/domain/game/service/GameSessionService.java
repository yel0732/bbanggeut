package com.A501.backend.domain.game.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.game.dto.request.SaveDobbleCardResultRequest;
import com.A501.backend.domain.game.dto.request.SaveGameResultRequest;
import com.A501.backend.domain.game.dto.response.GameSessionResponse;
import com.A501.backend.domain.game.entity.CardRound;
import com.A501.backend.domain.game.entity.GameSession;
import com.A501.backend.domain.game.metric.CardResultMetrics;
import com.A501.backend.domain.game.metric.DobbleResultCalculator;
import com.A501.backend.domain.game.metric.DobbleResultMetrics;
import com.A501.backend.domain.game.metric.GameMetricType;
import com.A501.backend.domain.game.repository.GameSessionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameSessionService {

    private final GameSessionRepository gameSessionRepository;
    private final GameSessionWriter gameSessionWriter;

    public boolean existsForChild(UUID gameSessionId, Long childId) {
        return gameSessionRepository.findById(gameSessionId)
                .filter(gameSession -> gameSession.getChildId().equals(childId))
                .isPresent();
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public GameSessionResponse saveCompletedResult(SaveGameResultRequest request) {
        validateTimeline(request);

        return gameSessionRepository.findByClientPlayId(request.clientPlayId())
                .map(GameSessionResponse::from)
                .orElseGet(() -> saveNewSession(request));
    }

    private GameSessionResponse saveNewSession(SaveGameResultRequest request) {
        if (request.gameType() == GameMetricType.DOBBLE) {
            return saveNewDobbleSession(request);
        }
        if (request.score() == null) {
            throw new IllegalArgumentException("도블 이외의 게임 결과에는 점수가 필요합니다.");
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

    private GameSessionResponse saveNewDobbleSession(SaveGameResultRequest request) {
        List<SaveDobbleCardResultRequest> requestedRounds = request.cardResults();
        validateDobbleRounds(requestedRounds);

        List<CardResultMetrics> cardResults = requestedRounds.stream()
                .map(SaveDobbleCardResultRequest::toMetrics)
                .toList();
        DobbleResultMetrics metrics = DobbleResultCalculator.calculate(cardResults);

        GameSession gameSession = GameSession.create(
                request.childId(),
                request.gameId(),
                request.clientPlayId(),
                null,
                request.selectedAt(),
                request.playStartedAt(),
                request.completedAt(),
                request.playDurationSeconds(),
                metrics.calculatedScore());

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
