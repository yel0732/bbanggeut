package com.A501.backend.domain.game.service;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.game.entity.CardRound;
import com.A501.backend.domain.game.entity.GameSession;
import com.A501.backend.domain.game.repository.CardRoundRepository;
import com.A501.backend.domain.game.repository.GameSessionRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class GameSessionWriter {

    private final GameSessionRepository gameSessionRepository;
    private final CardRoundRepository cardRoundRepository;

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
}
