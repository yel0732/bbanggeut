package com.A501.backend.domain.game.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.A501.backend.domain.game.dto.request.SaveGameResultRequest;
import com.A501.backend.domain.game.dto.response.GameSessionResponse;
import com.A501.backend.domain.game.entity.GameSession;
import com.A501.backend.domain.game.repository.GameRepository;
import com.A501.backend.domain.game.repository.GameSessionRepository;

@ExtendWith(MockitoExtension.class)
class GameSessionServiceTest {

    @Mock
    private GameSessionRepository gameSessionRepository;

    @Mock
    private GameRepository gameRepository;

    @Mock
    private GameSessionWriter gameSessionWriter;

    @Test
    void 게임_세션이_요청한_아이의_것인지_확인한다() {
        GameSession session = createSession(UUID.randomUUID());
        when(gameSessionRepository.findById(session.getGameSessionId())).thenReturn(Optional.of(session));
        GameSessionService service = new GameSessionService(
                gameSessionRepository, gameRepository, gameSessionWriter);

        assertThat(service.existsForChild(session.getGameSessionId(), 15L)).isTrue();
        assertThat(service.existsForChild(session.getGameSessionId(), 99L)).isFalse();
    }

    @Test
    void 동시_요청으로_UNIQUE_충돌이_발생하면_기존_세션을_반환한다() {
        UUID clientPlayId = UUID.randomUUID();
        SaveGameResultRequest request = createRequest(clientPlayId);
        GameSession existingSession = createSession(clientPlayId);
        when(gameSessionRepository.findByClientPlayId(clientPlayId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existingSession));
        when(gameSessionWriter.saveAndFlush(any(GameSession.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate client_play_id"));
        GameSessionService service = new GameSessionService(
                gameSessionRepository, gameRepository, gameSessionWriter);

        GameSessionResponse response = service.saveCompletedResult(request);

        assertThat(response.gameSessionId()).isEqualTo(existingSession.getGameSessionId());
        assertThat(response.clientPlayId()).isEqualTo(clientPlayId);
    }

    private SaveGameResultRequest createRequest(UUID clientPlayId) {
        LocalDateTime selectedAt = LocalDateTime.of(2026, 7, 23, 10, 0);
        return new SaveGameResultRequest(
                15L,
                7L,
                clientPlayId,
                "EASY",
                selectedAt,
                selectedAt.plusSeconds(10),
                selectedAt.plusSeconds(100),
                90,
                1200);
    }

    private GameSession createSession(UUID clientPlayId) {
        LocalDateTime selectedAt = LocalDateTime.of(2026, 7, 23, 10, 0);
        return GameSession.create(
                15L,
                7L,
                clientPlayId,
                "EASY",
                selectedAt,
                selectedAt.plusSeconds(10),
                selectedAt.plusSeconds(100),
                90,
                1200);
    }
}
