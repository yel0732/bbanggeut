package com.A501.backend.domain.game.service;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.game.entity.CardRound;
import com.A501.backend.domain.game.entity.GameSession;
import com.A501.backend.domain.game.entity.FruitNinjaFruitResult;
import com.A501.backend.domain.game.entity.FruitNinjaResult;
import com.A501.backend.domain.game.repository.CardRoundRepository;
import com.A501.backend.domain.game.repository.FruitNinjaFruitResultRepository;
import com.A501.backend.domain.game.repository.FruitNinjaResultRepository;
import com.A501.backend.domain.game.repository.GameSessionRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class GameSessionWriter {

    private final GameSessionRepository gameSessionRepository;
    private final CardRoundRepository cardRoundRepository;
    private final FruitNinjaResultRepository fruitNinjaResultRepository;
    private final FruitNinjaFruitResultRepository fruitNinjaFruitResultRepository;

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
}
