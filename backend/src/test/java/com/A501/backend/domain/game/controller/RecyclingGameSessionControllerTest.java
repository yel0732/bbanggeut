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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

import com.A501.backend.domain.game.repository.GameSessionRepository;
import com.A501.backend.domain.game.repository.RecyclingGameResultRepository;
import com.A501.backend.domain.game.service.GameChildAccessVerifier;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class RecyclingGameSessionControllerTest {

    @MockitoBean
    private GameChildAccessVerifier childAccessVerifier;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private GameSessionRepository gameSessionRepository;

    @Autowired
    private RecyclingGameResultRepository recyclingGameResultRepository;

    @BeforeEach
    void setUp() {
        recyclingGameResultRepository.deleteAll();
        gameSessionRepository.deleteAll();
        jdbcTemplate.update("delete from game where game_id in (7, 9)");
        insertGame(7L, "DOBBLE", "그림 맞추기");
        insertGame(9L, "RECYCLING", "분리수거");
    }

    @Test
    void savesRecyclingSessionAndReturnsStoredResult() throws Exception {
        UUID clientPlayId = UUID.randomUUID();

        mockMvc.perform(post("/api/game-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(clientPlayId, 15L)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gameSessionId").isNotEmpty())
                .andExpect(jsonPath("$.clientPlayId").value(clientPlayId.toString()))
                .andExpect(jsonPath("$.gameType").value("RECYCLING"))
                .andExpect(jsonPath("$.score").value(1200))
                .andExpect(jsonPath("$.recyclingResult.rulesVersion")
                        .value("RECYCLING_V1"))
                .andExpect(jsonPath("$.recyclingResult.successfulCount").value(12))
                .andExpect(jsonPath("$.recyclingResult.wrongCount").value(3));

        var session = gameSessionRepository.findByClientPlayId(clientPlayId).orElseThrow();
        assertThat(session.getPlayDurationSeconds()).isEqualTo(60);
        assertThat(session.getScore()).isEqualTo(1200);
        assertThat(recyclingGameResultRepository.findById(session.getGameSessionId()))
                .get()
                .extracting("rulesVersion", "successfulCount", "wrongCount")
                .containsExactly("RECYCLING_V1", 12, 3);
    }

    @Test
    void duplicateClientPlayIdReturnsExistingResultWithoutDuplicating() throws Exception {
        UUID clientPlayId = UUID.randomUUID();

        mockMvc.perform(post("/api/game-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(clientPlayId, 15L)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/game-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(clientPlayId, 15L)
                                .replace("\"wrongCount\": 3", "\"wrongCount\": 99")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recyclingResult.wrongCount").value(3));

        assertThat(gameSessionRepository.count()).isOne();
        assertThat(recyclingGameResultRepository.count()).isOne();
    }

    @Test
    void rejectsScoreThatDoesNotMatchSuccessfulCount() throws Exception {
        String request = validRequest(UUID.randomUUID(), 15L)
                .replace("\"score\": 1200", "\"score\": 1100");

        mockMvc.perform(post("/api/game-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_RECYCLING_RESULT"));

        assertNothingSaved();
    }

    @Test
    void rejectsPlayDurationOverSixtySeconds() throws Exception {
        String request = validRequest(UUID.randomUUID(), 15L)
                .replace("\"playDurationSeconds\": 60", "\"playDurationSeconds\": 61");

        mockMvc.perform(post("/api/game-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_RECYCLING_RESULT"));

        assertNothingSaved();
    }

    @Test
    void rejectsNegativeCounts() throws Exception {
        String request = validRequest(UUID.randomUUID(), 15L)
                .replace("\"wrongCount\": 3", "\"wrongCount\": -1");

        mockMvc.perform(post("/api/game-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));

        assertNothingSaved();
    }

    @Test
    void storesFrontendRuleVersionWithoutServerCatalogLookup() throws Exception {
        String request = validRequest(UUID.randomUUID(), 15L)
                .replace("RECYCLING_V1", "RECYCLING_UNKNOWN");

        mockMvc.perform(post("/api/game-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recyclingResult.rulesVersion")
                        .value("RECYCLING_UNKNOWN"));
    }

    @Test
    void rejectsGameIdThatDoesNotMatchRecyclingType() throws Exception {
        String request = validRequest(UUID.randomUUID(), 15L)
                .replace("\"gameId\": 9", "\"gameId\": 7");

        mockMvc.perform(post("/api/game-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("GAME_TYPE_MISMATCH"));

        assertNothingSaved();
    }

    @Test
    void rejectsRecyclingGameIdWithoutRecyclingType() throws Exception {
        String request = validRequest(UUID.randomUUID(), 15L)
                .replace("\"gameType\": \"RECYCLING\",", "");

        mockMvc.perform(post("/api/game-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("GAME_TYPE_MISMATCH"));

        assertNothingSaved();
    }

    @Test
    void rejectsClientPlayIdAlreadyUsedByAnotherChild() throws Exception {
        UUID clientPlayId = UUID.randomUUID();

        mockMvc.perform(post("/api/game-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(clientPlayId, 15L)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/game-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(clientPlayId, 16L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CLIENT_PLAY_ID_CONFLICT"));

        assertThat(gameSessionRepository.count()).isOne();
        assertThat(recyclingGameResultRepository.count()).isOne();
    }

    private void assertNothingSaved() {
        assertThat(gameSessionRepository.count()).isZero();
        assertThat(recyclingGameResultRepository.count()).isZero();
    }

    private void insertGame(long gameId, String gameCode, String gameName) {
        jdbcTemplate.update(
                """
                insert into game (game_id, game_code, game_name, created_at, updated_at)
                values (?, ?, ?, current_timestamp, current_timestamp)
                """,
                gameId,
                gameCode,
                gameName);
    }

    private String validRequest(UUID clientPlayId, Long childId) {
        return """
                {
                  "childId": %d,
                  "gameId": 9,
                  "clientPlayId": "%s",
                  "difficultyCode": null,
                  "selectedAt": "2026-07-31T10:00:00.123",
                  "playStartedAt": "2026-07-31T10:00:05.500",
                  "completedAt": "2026-07-31T10:01:05.500",
                  "playDurationSeconds": 60,
                  "score": 1200,
                  "gameType": "RECYCLING",
                  "recyclingResult": {
                    "rulesVersion": "RECYCLING_V1",
                    "successfulCount": 12,
                    "wrongCount": 3
                  }
                }
                """.formatted(childId, clientPlayId);
    }
}
