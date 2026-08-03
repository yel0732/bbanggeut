package com.A501.backend.domain.game.service;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.game.entity.CardRound;
import com.A501.backend.domain.game.entity.GameSession;
import com.A501.backend.domain.game.entity.FruitNinjaFruitResult;
import com.A501.backend.domain.game.entity.FruitNinjaResult;
import com.A501.backend.domain.game.entity.RecyclingGameResult;
import com.A501.backend.domain.game.entity.WordMatchProblemResult;
import com.A501.backend.domain.game.entity.WordMatchResult;
import com.A501.backend.domain.game.repository.CardRoundRepository;
import com.A501.backend.domain.game.repository.FruitNinjaFruitResultRepository;
import com.A501.backend.domain.game.repository.FruitNinjaResultRepository;
import com.A501.backend.domain.game.repository.GameSessionRepository;
import com.A501.backend.domain.game.repository.RecyclingGameResultRepository;
import com.A501.backend.domain.game.repository.WordMatchProblemResultRepository;
import com.A501.backend.domain.game.repository.WordMatchResultRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class GameSessionWriter {

    private final GameSessionRepository gameSessionRepository;
    private final CardRoundRepository cardRoundRepository;
    private final FruitNinjaResultRepository fruitNinjaResultRepository;
    private final FruitNinjaFruitResultRepository fruitNinjaFruitResultRepository;
    private final RecyclingGameResultRepository recyclingGameResultRepository;
    private final WordMatchResultRepository wordMatchResultRepository;
    private final WordMatchProblemResultRepository wordMatchProblemResultRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GameSession saveAndFlush(GameSession gameSession) {
        return gameSessionRepository.saveAndFlush(gameSession);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GameSession saveDobbleResult(
            GameSession gameSession,
            List<CardRound> cardRounds) {
        GameSession savedSession = gameSessionRepository.saveAndFlush(gameSession);
        cardRoundRepository.saveAll(cardRounds);
        cardRoundRepository.flush();
        return savedSession;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GameSession saveFruitNinjaResult(
            GameSession gameSession,
            FruitNinjaResult fruitNinjaResult,
            List<FruitNinjaFruitResult> fruitResults) {
        GameSession savedSession = gameSessionRepository.saveAndFlush(gameSession);
        fruitNinjaResultRepository.saveAndFlush(fruitNinjaResult);
        fruitNinjaFruitResultRepository.saveAll(fruitResults);
        fruitNinjaFruitResultRepository.flush();
        return savedSession;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GameSession saveRecyclingResult(
            GameSession gameSession,
            RecyclingGameResult recyclingGameResult) {
        GameSession savedSession = gameSessionRepository.saveAndFlush(gameSession);
        recyclingGameResultRepository.saveAndFlush(recyclingGameResult);
        return savedSession;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GameSession saveWordMatchResult(
            GameSession gameSession,
            WordMatchResult wordMatchResult,
            List<WordMatchProblemResult> problemResults) {
        GameSession savedSession = gameSessionRepository.saveAndFlush(gameSession);
        wordMatchResultRepository.saveAndFlush(wordMatchResult);
        wordMatchProblemResultRepository.saveAll(problemResults);
        wordMatchProblemResultRepository.flush();
        return savedSession;
    }
}
