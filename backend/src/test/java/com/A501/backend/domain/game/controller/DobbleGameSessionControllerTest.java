package com.A501.backend.domain.game.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.A501.backend.domain.game.repository.CardRoundRepository;
import com.A501.backend.domain.game.repository.GameSessionRepository;

@SpringBootTest
@AutoConfigureMockMvc
class DobbleGameSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GameSessionRepository gameSessionRepository;

    @Autowired
    private CardRoundRepository cardRoundRepository;

    @BeforeEach
    void setUp() {
        cardRoundRepository.deleteAll();
        gameSessionRepository.deleteAll();
    }

    @Test
    void 도블_완료_결과와_카드별_결과를_함께_저장하고_점수를_계산한다() throws Exception {
        UUID clientPlayId = UUID.randomUUID();

        mockMvc.perform(post("/api/game-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(clientPlayId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gameSessionId").isNotEmpty())
                .andExpect(jsonPath("$.clientPlayId").value(clientPlayId.toString()))
                .andExpect(jsonPath("$.score").value(2550));

        var savedSession = gameSessionRepository.findByClientPlayId(clientPlayId).orElseThrow();
        assertThat(savedSession.getScore()).isEqualTo(2550);
        assertThat(savedSession.getDifficultyCode()).isNull();
        assertThat(cardRoundRepository.findAllByGameSessionIdOrderByRoundNo(
                savedSession.getGameSessionId()))
                .extracting("roundNo")
                .containsExactly(1, 2, 3, 4);
    }

    @Test
    void 같은_clientPlayId를_재요청해도_세션과_카드_결과는_중복되지_않는다() throws Exception {
        UUID clientPlayId = UUID.randomUUID();

        mockMvc.perform(post("/api/game-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(clientPlayId)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/game-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(clientPlayId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.score").value(2550));

        assertThat(gameSessionRepository.count()).isEqualTo(1);
        assertThat(cardRoundRepository.count()).isEqualTo(4);
    }

    @Test
    void 카드_제시_순서가_중복되면_아무것도_저장하지_않는다() throws Exception {
        String request = validRequest(UUID.randomUUID())
                .replace("\"roundNo\": 4", "\"roundNo\": 3");

        mockMvc.perform(post("/api/game-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));

        assertThat(gameSessionRepository.count()).isZero();
        assertThat(cardRoundRepository.count()).isZero();
    }

    @Test
    void 완료_카드의_정답_소요_시간이_없으면_아무것도_저장하지_않는다() throws Exception {
        String request = validRequest(UUID.randomUUID())
                .replace("\"solveTimeMs\": 1000", "\"solveTimeMs\": null");

        mockMvc.perform(post("/api/game-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));

        assertThat(gameSessionRepository.count()).isZero();
        assertThat(cardRoundRepository.count()).isZero();
    }

    private String validRequest(UUID clientPlayId) {
        return """
                {
                  "childId": 15,
                  "gameId": 7,
                  "clientPlayId": "%s",
                  "selectedAt": "2026-07-23T10:00:00",
                  "playStartedAt": "2026-07-23T10:00:03",
                  "completedAt": "2026-07-23T10:02:03",
                  "playDurationSeconds": 120,
                  "gameType": "DOBBLE",
                  "cardResults": [
                    {
                      "roundNo": 1,
                      "comparisonCount": 1,
                      "wrongCount": 0,
                      "status": "COMPLETED",
                      "solveTimeMs": 1000
                    },
                    {
                      "roundNo": 2,
                      "comparisonCount": 2,
                      "wrongCount": 1,
                      "status": "COMPLETED",
                      "solveTimeMs": 2000
                    },
                    {
                      "roundNo": 3,
                      "comparisonCount": 5,
                      "wrongCount": 4,
                      "status": "COMPLETED",
                      "solveTimeMs": 3000
                    },
                    {
                      "roundNo": 4,
                      "comparisonCount": 1,
                      "wrongCount": 1,
                      "status": "INCOMPLETE",
                      "solveTimeMs": null
                    }
                  ]
                }
                """.formatted(clientPlayId);
    }
}
