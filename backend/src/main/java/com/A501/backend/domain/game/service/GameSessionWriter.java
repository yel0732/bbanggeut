package com.A501.backend.domain.game.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.game.entity.GameSession;
import com.A501.backend.domain.game.repository.GameSessionRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class GameSessionWriter {

    private final GameSessionRepository gameSessionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GameSession saveAndFlush(GameSession gameSession) {
        return gameSessionRepository.saveAndFlush(gameSession);
    }
}
