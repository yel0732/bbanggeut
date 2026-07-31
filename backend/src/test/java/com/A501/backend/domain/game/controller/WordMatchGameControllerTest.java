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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.A501.backend.domain.game.repository.GameSessionRepository;
import com.A501.backend.domain.game.repository.WordMatchProblemResultRepository;
import com.A501.backend.domain.game.repository.WordMatchResultRepository;
import com.A501.backend.domain.game.service.GameChildAccessVerifier;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class WordMatchGameControllerTest {

    @MockitoBean private GameChildAccessVerifier childAccessVerifier;
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private GameSessionRepository gameSessionRepository;
    @Autowired private WordMatchResultRepository wordMatchResultRepository;
    @Autowired private WordMatchProblemResultRepository problemResultRepository;

    @BeforeEach
    void setUp() {
        problemResultRepository.deleteAll();
        wordMatchResultRepository.deleteAll();
        gameSessionRepository.deleteAll();
        jdbcTemplate.update("delete from game where game_id = 10");
        jdbcTemplate.update("""
                insert into game (game_id, game_code, game_name, created_at, updated_at)
                values (10, 'WORD_MATCH', '낱말 맞추기', current_timestamp, current_timestamp)
                """);
    }

    @Test
    void savesFrontendProblemIdentifiersAndAggregatedResult() throws Exception {
        UUID clientPlayId = UUID.randomUUID();

        mockMvc.perform(post("/api/game-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(clientPlayId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gameType").value("WORD_MATCH"))
                .andExpect(jsonPath("$.score").value(1700))
                .andExpect(jsonPath("$.wordMatchResult.solvedWordCount").value(2))
                .andExpect(jsonPath("$.wordMatchResult.incompleteWordCount").value(1));

        var session = gameSessionRepository.findByClientPlayId(clientPlayId).orElseThrow();
        assertThat(wordMatchResultRepository.findById(session.getGameSessionId())).isPresent();
        assertThat(problemResultRepository.findAll()).hasSize(3)
                .extracting("problemCode", "wordLength")
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("APPLE", 2),
                        org.assertj.core.groups.Tuple.tuple("BUTTERFLY", 2),
                        org.assertj.core.groups.Tuple.tuple("TOMATO", 3));
    }

    @Test
    void duplicateClientPlayIdReturnsStoredResultWithoutDuplicating() throws Exception {
        UUID clientPlayId = UUID.randomUUID();
        mockMvc.perform(post("/api/game-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(clientPlayId)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/game-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(clientPlayId)
                                .replace("\"wrongCount\": 3,", "\"wrongCount\": 99,")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.wordMatchResult.wrongCount").value(3));

        assertThat(gameSessionRepository.count()).isOne();
        assertThat(problemResultRepository.count()).isEqualTo(3);
    }

    @Test
    void rejectsInvalidAggregateAndRollsBackEverything() throws Exception {
        mockMvc.perform(post("/api/game-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(UUID.randomUUID())
                                .replace("\"score\": 1700", "\"score\": 1800")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_WORD_MATCH_RESULT"));

        assertThat(gameSessionRepository.count()).isZero();
        assertThat(wordMatchResultRepository.count()).isZero();
        assertThat(problemResultRepository.count()).isZero();
    }

    @Test
    void rejectsCompletedProblemWithoutSolveTime() throws Exception {
        mockMvc.perform(post("/api/game-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(UUID.randomUUID())
                                .replace("\"solveTimeMs\": 8000", "\"solveTimeMs\": null")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_WORD_MATCH_RESULT"));
    }

    private String validRequest(UUID clientPlayId) {
        return """
                {
                  "childId": 15,
                  "gameId": 10,
                  "clientPlayId": "%s",
                  "selectedAt": "2026-07-31T10:00:00.123",
                  "playStartedAt": "2026-07-31T10:00:05.500",
                  "completedAt": "2026-07-31T10:01:35.500",
                  "playDurationSeconds": 90,
                  "score": 1700,
                  "gameType": "WORD_MATCH",
                  "wordMatchResult": {
                    "rulesVersion": "WORD_MATCH_V1",
                    "solvedWordCount": 2,
                    "incompleteWordCount": 1,
                    "wrongCount": 3,
                    "hintUsedCount": 1,
                    "problemResults": [
                      {
                        "problemCode": "APPLE",
                        "questionOrder": 1,
                        "wordLength": 2,
                        "status": "COMPLETED",
                        "wrongCount": 0,
                        "hintUsed": false,
                        "earnedScore": 1000,
                        "solveTimeMs": 8000
                      },
                      {
                        "problemCode": "BUTTERFLY",
                        "questionOrder": 2,
                        "wordLength": 2,
                        "status": "COMPLETED",
                        "wrongCount": 2,
                        "hintUsed": true,
                        "earnedScore": 700,
                        "solveTimeMs": 12000
                      },
                      {
                        "problemCode": "TOMATO",
                        "questionOrder": 3,
                        "wordLength": 3,
                        "status": "INCOMPLETE",
                        "wrongCount": 1,
                        "hintUsed": false,
                        "earnedScore": 0,
                        "solveTimeMs": null
                      }
                    ]
                  }
                }
                """.formatted(clientPlayId);
    }
}
