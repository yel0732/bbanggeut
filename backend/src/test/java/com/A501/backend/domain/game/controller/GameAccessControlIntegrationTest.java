package com.A501.backend.domain.game.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.A501.backend.domain.child.entity.Child;
import com.A501.backend.domain.child.repository.ChildRepository;
import com.A501.backend.domain.game.repository.GameSessionRepository;
import com.A501.backend.domain.game.service.SecurityGameCurrentMemberProvider;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@SpringBootTest(properties = {
        "app.jwt.secret=game-access-control-test-secret-key-123456789012"
})
@AutoConfigureMockMvc
@Import(GameAccessControlIntegrationTest.ProductionAuthConfig.class)
class GameAccessControlIntegrationTest {

    private static final Long PARENT_ID = 1L;
    private static final Long OTHER_PARENT_ID = 2L;
    private static final String SECRET =
            "game-access-control-test-secret-key-123456789012";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ChildRepository childRepository;

    @Autowired
    private GameSessionRepository gameSessionRepository;

    @BeforeEach
    void setUp() {
        gameSessionRepository.deleteAll();
        childRepository.deleteAll();
    }

    @Test
    void 액세스_토큰이_없으면_게임_결과_저장은_401() throws Exception {
        Long childId = seedChild(PARENT_ID);

        mockMvc.perform(post("/api/game-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(childId)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    void 다른_보호자의_자녀로_저장하면_404로_숨긴다() throws Exception {
        Long childId = seedChild(PARENT_ID);

        mockMvc.perform(post("/api/game-sessions")
                        .with(parentBearer(OTHER_PARENT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(childId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CHILD_NOT_FOUND"));
    }

    @Test
    void 소프트_삭제된_자녀는_404로_숨긴다() throws Exception {
        Long childId = seedDeletedChild(PARENT_ID);

        mockMvc.perform(post("/api/game-sessions")
                        .with(parentBearer(PARENT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(childId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CHILD_NOT_FOUND"));
    }

    @Test
    void 소유자는_게임_결과를_저장한다() throws Exception {
        Long childId = seedChild(PARENT_ID);

        mockMvc.perform(post("/api/game-sessions")
                        .with(parentBearer(PARENT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(childId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gameSessionId").isNotEmpty());
    }

    @Test
    void 액세스_토큰이_없으면_후르츠_규칙_조회는_401() throws Exception {
        mockMvc.perform(get("/api/games/fruit-ninja/rules/active"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    private Long seedChild(Long parentId) {
        return childRepository.saveAndFlush(
                Child.create(parentId, "테스터", "profile-presets/child-avatar")).getChildId();
    }

    private Long seedDeletedChild(Long parentId) {
        Child child = Child.create(parentId, "테스터", "profile-presets/child-avatar");
        child.softDelete(LocalDateTime.of(2026, 7, 29, 0, 0));
        return childRepository.saveAndFlush(child).getChildId();
    }

    private String validRequest(Long childId) {
        return """
                {
                  "childId": %d,
                  "gameId": 7,
                  "clientPlayId": "%s",
                  "difficultyCode": "EASY",
                  "selectedAt": "2026-07-29T10:00:00",
                  "playStartedAt": "2026-07-29T10:00:10",
                  "completedAt": "2026-07-29T10:01:40",
                  "playDurationSeconds": 90,
                  "score": 1200
                }
                """.formatted(childId, UUID.randomUUID());
    }

    private RequestPostProcessor parentBearer(Long parentId) {
        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(parentId));
            return request;
        };
    }

    private String accessToken(Long parentId) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(parentId))
                .claim("purpose", "ACCESS")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(key)
                .compact();
    }

    @TestConfiguration
    static class ProductionAuthConfig {

        @Bean
        @Primary
        SecurityGameCurrentMemberProvider securityGameCurrentMemberProvider() {
            return new SecurityGameCurrentMemberProvider();
        }
    }
}
