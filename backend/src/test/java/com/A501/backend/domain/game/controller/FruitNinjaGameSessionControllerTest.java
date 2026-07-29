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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.A501.backend.domain.game.service.GameChildAccessVerifier;
import com.A501.backend.domain.game.repository.FruitNinjaFruitResultRepository;
import com.A501.backend.domain.game.repository.FruitNinjaResultRepository;
import com.A501.backend.domain.game.repository.GameSessionRepository;

@SpringBootTest
@AutoConfigureMockMvc
class FruitNinjaGameSessionControllerTest {

    @MockitoBean
    private GameChildAccessVerifier childAccessVerifier;

    @Autowired MockMvc mockMvc;
    @Autowired GameSessionRepository gameSessionRepository;
    @Autowired FruitNinjaResultRepository fruitNinjaResultRepository;
    @Autowired FruitNinjaFruitResultRepository fruitResultRepository;

    @BeforeEach
    void setUp() {
        fruitResultRepository.deleteAll();
        fruitNinjaResultRepository.deleteAll();
        gameSessionRepository.deleteAll();
    }

    @Test
    void savesCompletedFruitNinjaResultWithClientScore() throws Exception {
        UUID clientPlayId = UUID.randomUUID();

        mockMvc.perform(post("/api/game-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(clientPlayId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientPlayId").value(clientPlayId.toString()))
                .andExpect(jsonPath("$.score").value(999999));

        var session = gameSessionRepository.findByClientPlayId(clientPlayId).orElseThrow();
        assertThat(session.getScore()).isEqualTo(999999);
        assertThat(fruitNinjaResultRepository.findById(session.getGameSessionId()))
                .get()
                .extracting("rulesVersion", "totalFruitCount", "slicedFruitCount")
                .containsExactly("fruit-v1", 10, 7);
        assertThat(fruitResultRepository.findAllByGameSessionIdOrderByFruitCode(
                session.getGameSessionId()))
                .extracting("fruitCode", "generatedCount", "slicedCount")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("APPLE", 6, 5),
                        org.assertj.core.groups.Tuple.tuple("BANANA", 4, 2));
    }

    @Test
    void duplicateClientPlayIdDoesNotDuplicateFruitResults() throws Exception {
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
        assertThat(fruitNinjaResultRepository.count()).isEqualTo(1);
        assertThat(fruitResultRepository.count()).isEqualTo(2);
    }

    @Test
    void acceptsClientPlayDurationWithoutRuleLookup() throws Exception {
        UUID clientPlayId = UUID.randomUUID();
        String request = validRequest(clientPlayId)
                .replace("\"playDurationSeconds\": 120", "\"playDurationSeconds\": 80");

        mockMvc.perform(post("/api/game-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated());

        assertThat(gameSessionRepository.findByClientPlayId(clientPlayId))
                .get()
                .extracting("playDurationSeconds")
                .isEqualTo(80);
    }

    @Test
    void rejectsManipulatedTotalsWithoutSavingAnything() throws Exception {
        String request = validRequest(UUID.randomUUID())
                .replace("\"slicedFruitCount\": 7", "\"slicedFruitCount\": 6");

        mockMvc.perform(post("/api/game-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest());

        assertNothingSaved();
    }

    @Test
    void rejectsSlicedCountGreaterThanGeneratedCount() throws Exception {
        String request = validRequest(UUID.randomUUID())
                .replace(
                        "\"fruitCode\": \"APPLE\", \"generatedCount\": 6, \"slicedCount\": 5",
                        "\"fruitCode\": \"APPLE\", \"generatedCount\": 4, \"slicedCount\": 5")
                .replace("\"totalFruitCount\": 10", "\"totalFruitCount\": 8");

        mockMvc.perform(post("/api/game-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest());

        assertNothingSaved();
    }

    @Test
    void storesClientRuleVersionWithoutRuleLookup() throws Exception {
        UUID clientPlayId = UUID.randomUUID();
        String request = validRequest(clientPlayId)
                .replace("\"rulesVersion\": \"fruit-v1\"", "\"rulesVersion\": \"unknown\"");

        mockMvc.perform(post("/api/game-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated());

        var session = gameSessionRepository.findByClientPlayId(clientPlayId).orElseThrow();
        assertThat(fruitNinjaResultRepository.findById(session.getGameSessionId()))
                .get()
                .extracting("rulesVersion")
                .isEqualTo("unknown");
    }

    @Test
    void storesClientFruitCodeWithoutRuleLookup() throws Exception {
        UUID clientPlayId = UUID.randomUUID();
        String request = validRequest(clientPlayId)
                .replace("\"fruitCode\": \"BANANA\"", "\"fruitCode\": \"MANGO\"");

        mockMvc.perform(post("/api/game-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated());

        var session = gameSessionRepository.findByClientPlayId(clientPlayId).orElseThrow();
        assertThat(fruitResultRepository.findAllByGameSessionIdOrderByFruitCode(
                session.getGameSessionId()))
                .extracting("fruitCode")
                .containsExactly("APPLE", "MANGO");
    }

    private void assertNothingSaved() {
        assertThat(gameSessionRepository.count()).isZero();
        assertThat(fruitNinjaResultRepository.count()).isZero();
        assertThat(fruitResultRepository.count()).isZero();
    }

    private String validRequest(UUID clientPlayId) {
        return """
                {
                  "childId": 15,
                  "gameId": 8,
                  "clientPlayId": "%s",
                  "selectedAt": "2026-07-23T10:00:00",
                  "playStartedAt": "2026-07-23T10:00:03",
                  "completedAt": "2026-07-23T10:02:03",
                  "playDurationSeconds": 120,
                  "score": 999999,
                  "gameType": "FRUIT_NINJA",
                  "fruitNinjaResult": {
                    "rulesVersion": "fruit-v1",
                    "totalFruitCount": 10,
                    "slicedFruitCount": 7,
                    "fruitResults": [
                      {"fruitCode": "APPLE", "generatedCount": 6, "slicedCount": 5},
                      {"fruitCode": "BANANA", "generatedCount": 4, "slicedCount": 2}
                    ]
                  }
                }
                """.formatted(clientPlayId);
    }
}
