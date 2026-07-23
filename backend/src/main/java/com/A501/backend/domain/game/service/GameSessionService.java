package com.A501.backend.domain.game.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.game.dto.request.SaveGameResultRequest;
import com.A501.backend.domain.game.dto.response.GameSessionResponse;
import com.A501.backend.domain.game.entity.GameSession;
import com.A501.backend.domain.game.repository.GameSessionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameSessionService {

    private final GameSessionRepository gameSessionRepository;

    @Transactional
    public GameSessionResponse saveCompletedResult(SaveGameResultRequest request) {
        validateTimeline(request);

        return gameSessionRepository.findByClientPlayId(request.clientPlayId())
                .map(GameSessionResponse::from)
                .orElseGet(() -> saveNewSession(request));
    }

    private GameSessionResponse saveNewSession(SaveGameResultRequest request) {
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

        return GameSessionResponse.from(gameSessionRepository.save(gameSession));
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
