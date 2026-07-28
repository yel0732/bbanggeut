package com.A501.backend.domain.game.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import com.A501.backend.domain.game.repository.FruitNinjaFruitResultRepository;
import com.A501.backend.domain.game.repository.FruitNinjaResultRepository;
import com.A501.backend.domain.game.repository.FruitScoreConfigRepository;
import com.A501.backend.domain.game.repository.FruitTypeRepository;
import com.A501.backend.domain.game.repository.GameSessionRepository;
import com.A501.backend.domain.game.repository.MovementGameRuleVersionRepository;

@SpringBootTest
@AutoConfigureMockMvc
class FruitNinjaRuleBootstrapServiceTest {

    @Autowired MockMvc mockMvc;
    @Autowired FruitNinjaRuleBootstrapService bootstrapService;
    @Autowired MovementGameRuleVersionRepository ruleVersionRepository;
    @Autowired FruitTypeRepository fruitTypeRepository;
    @Autowired FruitScoreConfigRepository scoreConfigRepository;
    @Autowired GameSessionRepository gameSessionRepository;
    @Autowired FruitNinjaResultRepository fruitNinjaResultRepository;
    @Autowired FruitNinjaFruitResultRepository fruitResultRepository;

    @BeforeEach
    void setUp() {
        fruitResultRepository.deleteAll();
        fruitNinjaResultRepository.deleteAll();
        gameSessionRepository.deleteAll();
        scoreConfigRepository.deleteAll();
        fruitTypeRepository.deleteAll();
        ruleVersionRepository.deleteAll();
        bootstrapService.initialize();
    }

    @Test
    void exposesFrontendCompatibleActiveRule() throws Exception {
        mockMvc.perform(get("/api/games/fruit-ninja/rules/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rulesVersion").value("fruit-ninja-v1"))
                .andExpect(jsonPath("$.plannedDurationSeconds").value(60))
                .andExpect(jsonPath("$.sliceSpeedThreshold").value(950.0))
                .andExpect(jsonPath("$.fruitSpawnIntervalMs").value(1100))
                .andExpect(jsonPath("$.maxVisibleFruit").value(5))
                .andExpect(jsonPath("$.fruits.length()").value(10))
                .andExpect(jsonPath("$.fruits[0].fruitCode").value("apple"))
                .andExpect(jsonPath("$.fruits[0].score").value(35))
                .andExpect(jsonPath("$.fruits[3].fruitCode").value("grape"))
                .andExpect(jsonPath("$.fruits[3].score").value(50));
    }

    @Test
    void initializesReferenceDataIdempotently() {
        bootstrapService.initialize();

        assertThat(ruleVersionRepository.count()).isEqualTo(1);
        assertThat(fruitTypeRepository.count()).isEqualTo(10);
        assertThat(scoreConfigRepository.count()).isEqualTo(10);
    }

    @Test
    void savesCompletedResultWithServerCalculatedScore() throws Exception {
        UUID clientPlayId = UUID.randomUUID();

        mockMvc.perform(post("/api/game-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "childId": 15,
                                  "gameId": 8,
                                  "clientPlayId": "%s",
                                  "selectedAt": "2026-07-28T10:00:00",
                                  "playStartedAt": "2026-07-28T10:00:03",
                                  "completedAt": "2026-07-28T10:01:03",
                                  "playDurationSeconds": 60,
                                  "score": 999999,
                                  "gameType": "FRUIT_NINJA",
                                  "fruitNinjaResult": {
                                    "rulesVersion": "fruit-ninja-v1",
                                    "totalFruitCount": 5,
                                    "slicedFruitCount": 3,
                                    "fruitResults": [
                                      {
                                        "fruitCode": "grape",
                                        "generatedCount": 3,
                                        "slicedCount": 2
                                      },
                                      {
                                        "fruitCode": "apple",
                                        "generatedCount": 2,
                                        "slicedCount": 1
                                      }
                                    ]
                                  }
                                }
                                """.formatted(clientPlayId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.score").value(135));

        assertThat(gameSessionRepository.findByClientPlayId(clientPlayId))
                .get()
                .extracting("score", "playDurationSeconds")
                .containsExactly(135, 60);
    }
}
