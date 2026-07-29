package com.A501.backend.domain.conversation.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;

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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.A501.backend.domain.child.entity.Child;
import com.A501.backend.domain.child.repository.ChildRepository;
import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.ConversationTopic;
import com.A501.backend.domain.conversation.repository.ConversationGameSessionRepository;
import com.A501.backend.domain.conversation.repository.ConversationSessionRepository;
import com.A501.backend.domain.conversation.repository.ConversationTopicStateRepository;
import com.A501.backend.domain.conversation.repository.ConversationTurnRepository;
import com.A501.backend.domain.conversation.service.RealtimeContextStore;
import com.A501.backend.domain.conversation.service.RealtimeSessionLeaseStore;
import com.A501.backend.domain.conversation.service.RealtimeTicketStore;
import com.A501.backend.domain.conversation.service.SecurityCurrentMemberProvider;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * conversation 도메인의 프로덕션 인증/인가 경로(!local 빈)를 검증한다.
 * 신규 빈이 @Profile("!local")이라 기본 local 컨텍스트에 뜨지 않으므로,
 * @TestConfiguration에서 @Primary로 등록해 local mock을 덮고 실제 로직만 실행한다.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=conversation-access-control-test-secret-key-1234567890"
})
@AutoConfigureMockMvc
@Import(ConversationAccessControlIntegrationTest.ProductionAuthConfig.class)
class ConversationAccessControlIntegrationTest {

    private static final Long PARENT_ID = 1L;
    private static final Long OTHER_PARENT_ID = 2L;
    private static final String SECRET =
            "conversation-access-control-test-secret-key-1234567890";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ChildRepository childRepository;

    @Autowired
    private ConversationSessionRepository sessionRepository;

    @Autowired
    private ConversationTopicStateRepository topicRepository;

    @Autowired
    private ConversationGameSessionRepository gameLinkRepository;

    @Autowired
    private ConversationTurnRepository turnRepository;

    @MockitoBean
    private RealtimeContextStore realtimeContextStore;

    @MockitoBean
    private RealtimeTicketStore realtimeTicketStore;

    @MockitoBean
    private RealtimeSessionLeaseStore realtimeSessionLeaseStore;

    @BeforeEach
    void setUp() {
        turnRepository.deleteAll();
        gameLinkRepository.deleteAll();
        topicRepository.deleteAll();
        sessionRepository.deleteAll();
        childRepository.deleteAll();
    }

    @Test
    void 액세스_토큰이_없으면_대화_생성은_401() throws Exception {
        Long childId = seedChild(PARENT_ID);

        mockMvc.perform(post("/api/v1/children/{childId}/conversations", childId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void 액세스_토큰이_없으면_realtime_세션_발급은_401() throws Exception {
        Long childId = seedChild(PARENT_ID);
        seedConversation("conv_rt_unauth", childId);

        mockMvc.perform(post(
                        "/api/v1/conversations/{conversationId}/realtime-session",
                        "conv_rt_unauth"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void 다른_보호자의_자녀로_생성하면_404로_숨긴다() throws Exception {
        Long childId = seedChild(PARENT_ID);

        mockMvc.perform(post("/api/v1/children/{childId}/conversations", childId)
                        .with(parentBearer(OTHER_PARENT_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CHILD_NOT_FOUND"));
    }

    @Test
    void 다른_보호자의_대화_조회는_404로_숨긴다() throws Exception {
        Long childId = seedChild(PARENT_ID);
        seedConversation("conv_other_get", childId);

        mockMvc.perform(get("/api/v1/conversations/{conversationId}", "conv_other_get")
                        .with(parentBearer(OTHER_PARENT_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CONVERSATION_NOT_FOUND"));
    }

    @Test
    void 다른_보호자의_대화_종료는_404로_숨긴다() throws Exception {
        Long childId = seedChild(PARENT_ID);
        ConversationSession session = seedConversation("conv_other_complete", childId);
        session.start();
        sessionRepository.saveAndFlush(session);

        mockMvc.perform(post("/api/v1/conversations/{conversationId}/complete", "conv_other_complete")
                        .with(parentBearer(OTHER_PARENT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"USER_FINISHED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CONVERSATION_NOT_FOUND"));
    }

    @Test
    void 소프트_삭제된_자녀는_404로_숨긴다() throws Exception {
        Long childId = seedDeletedChild(PARENT_ID);

        mockMvc.perform(post("/api/v1/children/{childId}/conversations", childId)
                        .with(parentBearer(PARENT_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CHILD_NOT_FOUND"));
    }

    @Test
    void 소유자는_대화를_생성한다() throws Exception {
        Long childId = seedChild(PARENT_ID);

        mockMvc.perform(post("/api/v1/children/{childId}/conversations", childId)
                        .with(parentBearer(PARENT_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.childId").value(childId));
    }

    @Test
    void jwks는_인증_없이_공개된다() throws Exception {
        mockMvc.perform(request(HttpMethod.GET, "/internal/v1/realtime/jwks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys").isArray());
    }

    private Long seedChild(Long parentId) {
        return childRepository.saveAndFlush(Child.create(parentId, "테스터", 1)).getChildId();
    }

    private Long seedDeletedChild(Long parentId) {
        Child child = Child.create(parentId, "테스터", 1);
        child.softDelete(LocalDateTime.of(2026, 7, 22, 0, 0));
        return childRepository.saveAndFlush(child).getChildId();
    }

    private ConversationSession seedConversation(String conversationId, Long childId) {
        return sessionRepository.saveAndFlush(
                ConversationSession.create(conversationId, childId, ConversationTopic.DAILY, 6));
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
        SecurityCurrentMemberProvider securityCurrentMemberProvider() {
            return new SecurityCurrentMemberProvider();
        }

        // 아이 소유권 검증은 이제 모든 프로필에서 child 테이블을 읽으므로 따로
        // 끼워 넣지 않는다. 보호자 판별만 local mock 대신 운영 구현으로 바꾼다.
    }
}
