package com.A501.backend.domain.game.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.A501.backend.domain.game.service.GameChildAccessVerifier;
import com.A501.backend.domain.game.metric.CardResultStatus;
import com.A501.backend.domain.game.metric.CognitiveGrade;
import com.A501.backend.domain.game.repository.CardRoundRepository;
import com.A501.backend.domain.game.repository.GameSessionRepository;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class DobbleGameSessionControllerTest {

    @MockitoBean
    private GameChildAccessVerifier childAccessVerifier;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GameSessionRepository gameSessionRepository;

    @Autowired
    private CardRoundRepository cardRoundRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cardRoundRepository.deleteAll();
        gameSessionRepository.deleteAll();
        jdbcTemplate.update("delete from game where game_id = 7");
        insertGame(7L, "DOBBLE", "그림 맞추기");
    }

    @Test
    void 도블_완료_결과와_카드별_결과를_함께_저장하고_점수를_계산한다() throws Exception {
        UUID clientPlayId = UUID.randomUUID();

        mockMvc.perform(post("/api/game-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(clientPlayId)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Location", startsWith("/api/game-sessions/")))
                .andExpect(jsonPath("$.gameSessionId").isNotEmpty())
                .andExpect(jsonPath("$.clientPlayId").value(clientPlayId.toString()))
                .andExpect(jsonPath("$.score").value(2550))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.difficultyCode").doesNotExist())
                .andExpect(jsonPath("$.cardResults").doesNotExist());

        var savedSession = gameSessionRepository.findByClientPlayId(clientPlayId).orElseThrow();
        assertThat(savedSession.getScore()).isEqualTo(2550);
        assertThat(savedSession.getDifficultyCode()).isNull();
        var savedRounds = cardRoundRepository.findAllByGameSessionIdOrderByRoundNo(
                savedSession.getGameSessionId());

        assertThat(savedRounds)
                .extracting("roundNo")
                .containsExactly(1, 2, 3, 4);
        assertThat(savedRounds)
                .extracting("finalGrade")
                .containsExactly(
                        CognitiveGrade.PERFECT,
                        CognitiveGrade.EXCELLENT,
                        CognitiveGrade.GOOD,
                        null);
        assertThat(savedRounds)
                .extracting("solveTimeMs")
                .containsExactly(1000L, 2000L, 3000L, null);
        assertThat(savedRounds)
                .extracting("status")
                .containsExactly(
                        CardResultStatus.COMPLETED,
                        CardResultStatus.COMPLETED,
                        CardResultStatus.COMPLETED,
                        CardResultStatus.INCOMPLETE);
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
                .andExpect(jsonPath("$.error").value("INVALID_DOBBLE_RESULT"));

        assertThat(gameSessionRepository.count()).isZero();
        assertThat(cardRoundRepository.count()).isZero();
    }

    @Test
    void 도블_결과가_비어_있으면_요청을_거부한다() throws Exception {
        String request = validRequest(UUID.randomUUID())
                .replaceFirst("(?s)\"cardResults\": \\[.*\\]", "\"cardResults\": []");

        mockMvc.perform(post("/api/game-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));

        assertNothingSaved();
    }

    @Test
    void 오답_횟수가_판정_횟수보다_크면_요청을_거부한다() throws Exception {
        String request = validRequest(UUID.randomUUID())
                .replaceFirst("\"wrongCount\": 0", "\"wrongCount\": 2");

        mockMvc.perform(post("/api/game-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_DOBBLE_RESULT"));

        assertNothingSaved();
    }

    @Test
    void 완료_카드의_판정_횟수가_0이면_요청을_거부한다() throws Exception {
        String request = validRequest(UUID.randomUUID())
                .replaceFirst("\"comparisonCount\": 1", "\"comparisonCount\": 0");

        mockMvc.perform(post("/api/game-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_DOBBLE_RESULT"));

        assertNothingSaved();
    }

    @Test
    void 미완료_카드에_맞춘_시간이_있으면_요청을_거부한다() throws Exception {
        String request = validRequest(UUID.randomUUID())
                .replaceFirst(
                        "\"status\": \"INCOMPLETE\",\\s+\"solveTimeMs\": null",
                        "\"status\": \"INCOMPLETE\",\\n      \"solveTimeMs\": 4000");

        mockMvc.perform(post("/api/game-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));

        assertNothingSaved();
    }

    @Test
    void 카드_제시_순서가_0이면_요청을_거부한다() throws Exception {
        String request = validRequest(UUID.randomUUID())
                .replaceFirst("\"roundNo\": 1", "\"roundNo\": 0");

        mockMvc.perform(post("/api/game-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors['cardResults[0].roundNo']").exists());

        assertNothingSaved();
    }

    @Test
    void rejectsManipulatedScore() throws Exception {
        String request = validRequest(UUID.randomUUID())
                .replace("\"score\": 2550", "\"score\": 2551");

        mockMvc.perform(post("/api/game-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_DOBBLE_RESULT"));
        assertNothingSaved();
    }

    @Test
    void rejectsPlayDurationOverGameLimit() throws Exception {
        String request = validRequest(UUID.randomUUID())
                .replace("\"playDurationSeconds\": 90", "\"playDurationSeconds\": 91");

        mockMvc.perform(post("/api/game-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_DOBBLE_RESULT"));
        assertNothingSaved();
    }

    private void assertNothingSaved() {
        assertThat(gameSessionRepository.count()).isZero();
        assertThat(cardRoundRepository.count()).isZero();
    }

    private void insertGame(long gameId, String gameCode, String gameName) {
        jdbcTemplate.update(
                "insert into game (game_id, game_code, game_name, created_at, updated_at) "
                        + "values (?, ?, ?, current_timestamp, current_timestamp)",
                gameId, gameCode, gameName);
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
                  "playDurationSeconds": 90,
                  "score": 2550,
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
