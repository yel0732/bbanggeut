package com.A501.backend.domain.conversation.controller;

import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.A501.backend.domain.conversation.entity.ContextType;
import com.A501.backend.domain.conversation.entity.ConversationGoal;
import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.TerminationReason;
import com.A501.backend.domain.conversation.exception.ConversationErrorCode;
import com.A501.backend.domain.conversation.exception.ConversationException;
import com.A501.backend.domain.conversation.repository.ConversationGameSessionRepository;
import com.A501.backend.domain.conversation.repository.ConversationSessionRepository;
import com.A501.backend.domain.conversation.repository.ConversationTopicStateRepository;
import com.A501.backend.domain.conversation.repository.ConversationTurnRepository;
import com.A501.backend.domain.conversation.service.ChildAccessVerifier;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ConversationSessionControllerTest.FixedClockConfig.class)
class ConversationSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ConversationSessionRepository sessionRepository;

    @Autowired
    private ConversationTopicStateRepository topicRepository;

    @Autowired
    private ConversationGameSessionRepository gameLinkRepository;

    @Autowired
    private ConversationTurnRepository turnRepository;

    @MockitoBean
    private ChildAccessVerifier childAccessVerifier;

    @BeforeEach
    void setUp() {
        turnRepository.deleteAll();
        gameLinkRepository.deleteAll();
        topicRepository.deleteAll();
        sessionRepository.deleteAll();
    }

    @Test
    void 요청_본문_없이_DAILY_세션을_자동_생성한다() throws Exception {
        mockMvc.perform(post("/api/v1/children/15/conversations"))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        org.hamcrest.Matchers.matchesPattern(
                                "/api/v1/conversations/conv_[0-9a-f]{32}")))
                .andExpect(jsonPath("$.conversationId").value(
                        org.hamcrest.Matchers.matchesPattern("conv_[0-9a-f]{32}")))
                .andExpect(jsonPath("$.childId").value(15))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.currentTopic").value("DAILY"))
                .andExpect(jsonPath("$.totalQuestionCount").value(0))
                .andExpect(jsonPath("$.topics.length()").value(1))
                .andExpect(jsonPath("$.topics[0].type").value("DAILY"))
                .andExpect(jsonPath("$.topics[0].order").value(1))
                .andExpect(jsonPath("$.topics[0].status").value("PENDING"))
                .andExpect(jsonPath("$.topics[0].questionCount").value(0))
                .andExpect(jsonPath("$.topics[0].maxQuestionCount").value(6))
                .andExpect(jsonPath("$.createdAt").value(
                        org.hamcrest.Matchers.matchesPattern(
                                "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?\\+09:00")));
    }

    @Test
    void 요청_본문은_더_이상_필요하지_않다() throws Exception {
        mockMvc.perform(post("/api/v1/children/15/conversations")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());
    }

    @Test
    void 활성_세션이_있으면_409를_반환한다() throws Exception {
        sessionRepository.save(ConversationSession.create(
                "conv_existing",
                15L,
                null,
                ContextType.DAILY,
                ConversationGoal.DIARY,
                6));

        mockMvc.perform(post("/api/v1/children/15/conversations"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ACTIVE_CONVERSATION_EXISTS"));
    }

    @Test
    void 자녀_접근_권한이_없으면_403을_반환한다() throws Exception {
        doThrow(new ConversationException(ConversationErrorCode.CHILD_ACCESS_DENIED))
                .when(childAccessVerifier)
                .verifyAccess(1L, 15L);

        mockMvc.perform(post("/api/v1/children/15/conversations"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("CHILD_ACCESS_DENIED"));
    }

    @Test
    void 생성한_세션은_주제_상태와_함께_조회된다() throws Exception {
        String location = mockMvc.perform(
                        post("/api/v1/children/15/conversations"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.childId").value(15))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.currentTopic").value("DAILY"))
                .andExpect(jsonPath("$.totalQuestionCount").value(0))
                .andExpect(jsonPath("$.topics[0].type").value("DAILY"))
                .andExpect(jsonPath("$.topics[0].status").value("PENDING"))
                .andExpect(jsonPath("$.currentSessionEpoch").value(0))
                .andExpect(jsonPath("$.startedAt").isEmpty())
                .andExpect(jsonPath("$.completedAt").isEmpty())
                .andExpect(jsonPath("$.terminationReason").isEmpty());
    }

    @Test
    void 존재하지_않는_세션은_404를_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/conversations/conv_missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CONVERSATION_NOT_FOUND"));
    }

    @Test
    void 진행_중_세션을_종료하면_KST_완료_시각을_반환한다() throws Exception {
        ConversationSession session = ConversationSession.create(
                "conv_complete_api",
                15L,
                null,
                ContextType.DAILY,
                ConversationGoal.DIARY,
                6);
        session.start();
        sessionRepository.saveAndFlush(session);

        mockMvc.perform(post(
                        "/api/v1/conversations/{conversationId}/complete",
                        session.getConversationId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"USER_FINISHED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value("conv_complete_api"))
                // 종료 버튼으로 끝낸 대화는 완주가 아니다 — ABORTED로 남고 내용은 정리된다.
                .andExpect(jsonPath("$.status").value("ABORTED"))
                .andExpect(jsonPath("$.terminationReason").value("USER_FINISHED"))
                .andExpect(jsonPath("$.completedAt")
                        .value("2026-07-22T10:08:00+09:00"));
    }

    @Test
    void 클라이언트가_완주_사유로_종료하면_400을_반환한다() throws Exception {
        ConversationSession session = ConversationSession.create(
                "conv_complete_guard",
                15L,
                null,
                ContextType.DAILY,
                ConversationGoal.DIARY,
                6);
        session.start();
        sessionRepository.saveAndFlush(session);

        mockMvc.perform(post(
                        "/api/v1/conversations/{conversationId}/complete",
                        session.getConversationId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"REQUIRED_SLOTS_FILLED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_TERMINATION_REASON"));
    }

    @Test
    void 완료_세션은_KST_오프셋으로_조회된다() throws Exception {
        ConversationSession session = ConversationSession.create(
                "conv_completed_api",
                15L,
                null,
                ContextType.DAILY,
                ConversationGoal.DIARY,
                6);
        session.start(LocalDateTime.of(2026, 7, 22, 1, 0, 10));
        session.terminate(
                LocalDateTime.of(2026, 7, 22, 1, 8),
                TerminationReason.REQUIRED_SLOTS_FILLED);
        sessionRepository.save(session);

        mockMvc.perform(get(
                        "/api/v1/conversations/{conversationId}",
                        session.getConversationId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.startedAt")
                        .value("2026-07-22T10:00:10+09:00"))
                .andExpect(jsonPath("$.completedAt")
                        .value("2026-07-22T10:08:00+09:00"))
                .andExpect(jsonPath("$.terminationReason").value("REQUIRED_SLOTS_FILLED"));
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedUtcClock() {
            return Clock.fixed(
                    Instant.parse("2026-07-22T01:08:00Z"),
                    ZoneOffset.UTC);
        }
    }
}
