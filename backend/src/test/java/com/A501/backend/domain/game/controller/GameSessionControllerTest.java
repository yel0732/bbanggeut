package com.A501.backend.domain.game.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.A501.backend.domain.game.repository.GameSessionRepository;

@SpringBootTest
@AutoConfigureMockMvc
class GameSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GameSessionRepository gameSessionRepository;

    @BeforeEach
    void setUp() {
        gameSessionRepository.deleteAll();
    }

    @Test
    void 정상_완료된_게임_결과를_DB에_저장한다() throws Exception {
        UUID clientPlayId = UUID.randomUUID();

        mockMvc.perform(post("/api/game-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(clientPlayId)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.gameSessionId").isNotEmpty())
                .andExpect(jsonPath("$.clientPlayId").value(clientPlayId.toString()))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        assertThat(gameSessionRepository.findByClientPlayId(clientPlayId)).isPresent();
    }

    @Test
    void 같은_client_play_id로_재요청해도_한_건만_저장한다() throws Exception {
        UUID clientPlayId = UUID.randomUUID();

        mockMvc.perform(post("/api/game-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(clientPlayId)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/game-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(clientPlayId)))
                .andExpect(status().isCreated());

        assertThat(gameSessionRepository.count()).isEqualTo(1);
    }

    @Test
    void 완료_시각이_시작_시각보다_빠르면_저장하지_않는다() throws Exception {
        String request = """
                {
                  "childId": 15,
                  "gameId": 7,
                  "clientPlayId": "%s",
                  "difficultyCode": "EASY",
                  "selectedAt": "2026-07-23T10:00:00",
                  "playStartedAt": "2026-07-23T10:00:10",
                  "completedAt": "2026-07-23T10:00:05",
                  "playDurationSeconds": 90,
                  "score": 1200
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/game-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));

        assertThat(gameSessionRepository.count()).isZero();
    }

    @Test
    void 점수가_누락되면_저장하지_않는다() throws Exception {
        String request = """
                {
                  "childId": 15,
                  "gameId": 7,
                  "clientPlayId": "%s",
                  "selectedAt": "2026-07-23T10:00:00",
                  "playStartedAt": "2026-07-23T10:00:10",
                  "completedAt": "2026-07-23T10:01:40",
                  "playDurationSeconds": 90
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/game-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.score").exists());

        assertThat(gameSessionRepository.count()).isZero();
    }

    private String validRequest(UUID clientPlayId) {
        return """
                {
                  "childId": 15,
                  "gameId": 7,
                  "clientPlayId": "%s",
                  "difficultyCode": "EASY",
                  "selectedAt": "2026-07-23T10:00:00",
                  "playStartedAt": "2026-07-23T10:00:10",
                  "completedAt": "2026-07-23T10:01:40",
                  "playDurationSeconds": 90,
                  "score": 1200
                }
                """.formatted(clientPlayId);
    }
}
