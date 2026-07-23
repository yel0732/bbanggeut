package com.A501.backend.domain.conversation.controller;

import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.math.BigDecimal;

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
import org.springframework.test.util.ReflectionTestUtils;

import com.A501.backend.domain.conversation.entity.ContextType;
import com.A501.backend.domain.conversation.entity.ConversationGoal;
import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.ConversationTurn;
import com.A501.backend.domain.conversation.entity.TerminationReason;
import com.A501.backend.domain.conversation.exception.ConversationErrorCode;
import com.A501.backend.domain.conversation.exception.ConversationException;
import com.A501.backend.domain.conversation.repository.ConversationSessionRepository;
import com.A501.backend.domain.conversation.repository.ConversationTurnRepository;
import com.A501.backend.domain.conversation.service.ChildAccessVerifier;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ConversationSessionControllerTest.FixedClockConfig.class)
class ConversationSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ConversationSessionRepository conversationSessionRepository;

    @Autowired
    private ConversationTurnRepository conversationTurnRepository;

    @MockitoBean
    private ChildAccessVerifier childAccessVerifier;

    @BeforeEach
    void setUp() {
        conversationTurnRepository.deleteAll();
        conversationSessionRepository.deleteAll();
    }

    @Test
    void 정상_DAILY_요청은_세션을_생성하고_201을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/children/15/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dailyRequest(6)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(
                        "/api/v1/conversations/conv_[0-9a-f]{32}")))
                .andExpect(jsonPath("$.conversationId").value(org.hamcrest.Matchers.matchesPattern(
                        "conv_[0-9a-f]{32}")))
                .andExpect(jsonPath("$.childId").value(15))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.contextType").value("DAILY"))
                .andExpect(jsonPath("$.conversationGoal").value("DIARY"))
                .andExpect(jsonPath("$.questionCount").value(0))
                .andExpect(jsonPath("$.maxQuestionCount").value(6))
                .andExpect(jsonPath("$.createdAt").value(org.hamcrest.Matchers.matchesPattern(
                        "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?\\+09:00")));
    }

    @Test
    void 활성_세션이_있으면_409를_반환한다() throws Exception {
        conversationSessionRepository.save(ConversationSession.create(
                "conv_existing", 15L, null, ContextType.DAILY, ConversationGoal.DIARY, 6));

        mockMvc.perform(post("/api/v1/children/15/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dailyRequest(6)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ACTIVE_CONVERSATION_EXISTS"));
    }

    @Test
    void 접근_권한이_없으면_403을_반환한다() throws Exception {
        doThrow(new ConversationException(ConversationErrorCode.CHILD_ACCESS_DENIED))
                .when(childAccessVerifier).verifyAccess(1L, 15L);

        mockMvc.perform(post("/api/v1/children/15/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dailyRequest(6)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("CHILD_ACCESS_DENIED"));
    }

    @Test
    void 아이가_없으면_404를_반환한다() throws Exception {
        doThrow(new ConversationException(ConversationErrorCode.CHILD_NOT_FOUND))
                .when(childAccessVerifier).verifyAccess(1L, 15L);

        mockMvc.perform(post("/api/v1/children/15/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dailyRequest(6)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CHILD_NOT_FOUND"));
    }

    @Test
    void 최대_질문_수를_벗어나면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/children/15/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dailyRequest(7)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_MAX_QUESTION_COUNT"));
    }

    @Test
    void 필수값이_없거나_Enum이_잘못되면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/children/15/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contextType":"DAILY","gameSessionId":null,"maxQuestionCount":6}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/children/15/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contextType":"UNKNOWN","conversationGoal":"DIARY","maxQuestionCount":6}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void GAME과_DAILY의_게임_세션_ID_조합이_잘못되면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/children/15/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contextType":"GAME","gameSessionId":null,
                                 "conversationGoal":"DIARY","maxQuestionCount":6}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_CONVERSATION_CONTEXT"));
    }

    @Test
    void 존재하지_않는_게임_세션이면_404를_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/children/15/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contextType":"GAME",
                                 "gameSessionId":"550e8400-e29b-41d4-a716-446655440000",
                                 "conversationGoal":"DIARY","maxQuestionCount":6}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("GAME_SESSION_NOT_FOUND"));
    }

    @Test
    void 생성한_READY_세션을_Location_경로로_조회한다() throws Exception {
        ConversationSession session = conversationSessionRepository.save(ConversationSession.create(
                "conv_query_api", 15L, null, ContextType.DAILY, ConversationGoal.DIARY, 6));

        mockMvc.perform(get("/api/v1/conversations/{conversationId}", session.getConversationId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value("conv_query_api"))
                .andExpect(jsonPath("$.childId").value(15))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.contextType").value("DAILY"))
                .andExpect(jsonPath("$.conversationGoal").value("DIARY"))
                .andExpect(jsonPath("$.questionCount").value(0))
                .andExpect(jsonPath("$.maxQuestionCount").value(6))
                .andExpect(jsonPath("$.currentSessionEpoch").value(0))
                .andExpect(jsonPath("$.startedAt").isEmpty())
                .andExpect(jsonPath("$.completedAt").isEmpty())
                .andExpect(jsonPath("$.terminationReason").isEmpty());
    }

    @Test
    void 완료된_세션의_UTC_시각을_KST_오프셋으로_조회한다() throws Exception {
        ConversationSession session = ConversationSession.create(
                "conv_completed_api", 15L, null, ContextType.DAILY, ConversationGoal.DIARY, 6);
        session.start();
        session.terminate(LocalDateTime.of(2026, 7, 22, 1, 8), TerminationReason.USER_FINISHED);
        ReflectionTestUtils.setField(session, "startedAt", LocalDateTime.of(2026, 7, 22, 1, 0, 10));
        conversationSessionRepository.save(session);

        mockMvc.perform(get("/api/v1/conversations/{conversationId}", session.getConversationId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.startedAt").value("2026-07-22T10:00:10+09:00"))
                .andExpect(jsonPath("$.completedAt").value("2026-07-22T10:08:00+09:00"))
                .andExpect(jsonPath("$.terminationReason").value("USER_FINISHED"));
    }

    @Test
    void 진행_중_세션을_종료하고_KST_완료_시각을_반환한다() throws Exception {
        ConversationSession session = ConversationSession.create(
                "conv_complete_api", 15L, null, ContextType.DAILY, ConversationGoal.DIARY, 6);
        session.start();
        conversationSessionRepository.saveAndFlush(session);

        mockMvc.perform(post("/api/v1/conversations/{conversationId}/complete", session.getConversationId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"USER_FINISHED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value("conv_complete_api"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.questionCount").value(0))
                .andExpect(jsonPath("$.terminationReason").value("USER_FINISHED"))
                .andExpect(jsonPath("$.completedAt").value("2026-07-22T10:08:00+09:00"));

        mockMvc.perform(get("/api/v1/conversations/{conversationId}", session.getConversationId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.terminationReason").value("USER_FINISHED"))
                .andExpect(jsonPath("$.completedAt").value("2026-07-22T10:08:00+09:00"));
    }

    @Test
    void 다른_사유로_종료를_반복해도_최초_결과를_유지한다() throws Exception {
        ConversationSession session = ConversationSession.create(
                "conv_idempotent_api", 15L, null, ContextType.DAILY, ConversationGoal.DIARY, 6);
        session.start();
        conversationSessionRepository.saveAndFlush(session);

        mockMvc.perform(post("/api/v1/conversations/{conversationId}/complete", session.getConversationId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"USER_FINISHED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/conversations/{conversationId}/complete", session.getConversationId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"SYSTEM_ERROR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.terminationReason").value("USER_FINISHED"))
                .andExpect(jsonPath("$.completedAt").value("2026-07-22T10:08:00+09:00"));
    }

    @Test
    void 잘못된_종료_사유는_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/conversations/conv_invalid_reason/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_TERMINATION_REASON"));

        mockMvc.perform(post("/api/v1/conversations/conv_invalid_reason/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_TERMINATION_REASON"));

        mockMvc.perform(post("/api/v1/conversations/conv_invalid_reason/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void READY_세션을_정상_완료하려고_하면_409를_반환한다() throws Exception {
        conversationSessionRepository.saveAndFlush(ConversationSession.create(
                "conv_invalid_state", 15L, null, ContextType.DAILY, ConversationGoal.DIARY, 6));

        mockMvc.perform(post("/api/v1/conversations/conv_invalid_state/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"USER_FINISHED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_SESSION_STATE"));
    }

    @Test
    void 없는_세션을_종료하면_404를_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/conversations/conv_complete_missing/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"USER_FINISHED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CONVERSATION_NOT_FOUND"));
    }

    @Test
    void 접근할_수_없는_세션을_종료하면_403을_반환한다() throws Exception {
        ConversationSession session = ConversationSession.create(
                "conv_complete_denied", 15L, null, ContextType.DAILY, ConversationGoal.DIARY, 6);
        session.start();
        conversationSessionRepository.saveAndFlush(session);
        doThrow(new ConversationException(ConversationErrorCode.CHILD_ACCESS_DENIED))
                .when(childAccessVerifier).verifyAccess(1L, 15L);

        mockMvc.perform(post("/api/v1/conversations/conv_complete_denied/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"USER_FINISHED\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("CONVERSATION_ACCESS_DENIED"));
    }

    @Test
    void OpenAPI에_허용된_종료_사유를_모두_노출한다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.components.schemas.CompleteConversationRequest.properties.reason.enum")
                        .value(org.hamcrest.Matchers.containsInAnyOrder(
                                "USER_FINISHED",
                                "MAX_QUESTION_REACHED",
                                "CHILD_REQUESTED_STOP",
                                "INACTIVITY_TIMEOUT",
                                "CONNECTION_LOST",
                                "PAGE_CLOSED",
                                "SYSTEM_ERROR")));
    }

    @Test
    void 존재하지_않는_세션을_조회하면_404를_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/conversations/conv_missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CONVERSATION_NOT_FOUND"));
    }

    @Test
    void 접근할_수_없는_세션을_조회하면_403을_반환한다() throws Exception {
        conversationSessionRepository.save(ConversationSession.create(
                "conv_denied_api", 15L, null, ContextType.DAILY, ConversationGoal.DIARY, 6));
        doThrow(new ConversationException(ConversationErrorCode.CHILD_ACCESS_DENIED))
                .when(childAccessVerifier).verifyAccess(1L, 15L);

        mockMvc.perform(get("/api/v1/conversations/conv_denied_api"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("CONVERSATION_ACCESS_DENIED"));
    }

    @Test
    void 대화_턴을_순서대로_조회하고_중단_턴과_STT_신뢰도를_포함한다() throws Exception {
        conversationSessionRepository.save(ConversationSession.create(
                "conv_turns_api", 15L, null, ContextType.DAILY, ConversationGoal.DIARY, 6));
        LocalDateTime startedAt = LocalDateTime.of(2026, 7, 22, 1, 0);
        ConversationTurn aiCompleted = ConversationTurn.createAiGenerating(
                "turn_api_001", "conv_turns_api", 1, "오늘 어땠어?",
                "resp_api_001", startedAt);
        aiCompleted.complete(startedAt.plusSeconds(2));
        ConversationTurn childCompleted = ConversationTurn.createChildCompleted(
                "turn_api_002", "conv_turns_api", 2, "재미있었어요",
                new BigDecimal("0.9300"), startedAt.plusSeconds(3), startedAt.plusSeconds(5));
        ConversationTurn aiInterrupted = ConversationTurn.createAiInterrupted(
                "turn_api_003", "conv_turns_api", 3, "그럼 가장...",
                "resp_api_003", startedAt.plusSeconds(6), startedAt.plusSeconds(7),
                "CHILD_BARGE_IN", "int_api_001");
        conversationTurnRepository.saveAllAndFlush(
                java.util.List.of(aiInterrupted, childCompleted, aiCompleted));

        mockMvc.perform(get("/api/v1/conversations/conv_turns_api/turns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value("conv_turns_api"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.turns[0].turnId").value("turn_api_001"))
                .andExpect(jsonPath("$.turns[0].speaker").value("AI"))
                .andExpect(jsonPath("$.turns[0].startedAt").value("2026-07-22T10:00:00+09:00"))
                .andExpect(jsonPath("$.turns[1].turnId").value("turn_api_002"))
                .andExpect(jsonPath("$.turns[1].speaker").value("CHILD"))
                .andExpect(jsonPath("$.turns[1].sttConfidence").value(0.93))
                .andExpect(jsonPath("$.turns[2].turnId").value("turn_api_003"))
                .andExpect(jsonPath("$.turns[2].status").value("INTERRUPTED"))
                .andExpect(jsonPath("$.turns[2].completedAt").isEmpty());
    }

    @Test
    void 화자_필터와_페이지_조건을_적용한다() throws Exception {
        conversationSessionRepository.save(ConversationSession.create(
                "conv_filter_api", 15L, null, ContextType.DAILY, ConversationGoal.DIARY, 6));
        LocalDateTime startedAt = LocalDateTime.of(2026, 7, 22, 1, 0);
        ConversationTurn first = ConversationTurn.createChildCompleted(
                "turn_filter_001", "conv_filter_api", 1, "첫 답변",
                null, startedAt, startedAt.plusSeconds(1));
        ConversationTurn second = ConversationTurn.createChildCompleted(
                "turn_filter_002", "conv_filter_api", 2, "둘째 답변",
                null, startedAt.plusSeconds(2), startedAt.plusSeconds(3));
        conversationTurnRepository.saveAllAndFlush(java.util.List.of(first, second));

        mockMvc.perform(get("/api/v1/conversations/conv_filter_api/turns")
                        .param("speaker", "CHILD")
                        .param("page", "1")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.turns[0].turnId").value("turn_filter_002"));
    }

    @Test
    void 잘못된_화자와_페이지_조건은_400을_반환한다() throws Exception {
        conversationSessionRepository.save(ConversationSession.create(
                "conv_invalid_filter", 15L, null, ContextType.DAILY, ConversationGoal.DIARY, 6));

        mockMvc.perform(get("/api/v1/conversations/conv_invalid_filter/turns")
                        .param("speaker", "PARENT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_SPEAKER"));

        mockMvc.perform(get("/api/v1/conversations/conv_invalid_filter/turns")
                        .param("page", "-1")
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    void 없는_대화의_턴_목록은_404를_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/conversations/conv_turns_missing/turns"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CONVERSATION_NOT_FOUND"));
    }

    @Test
    void 숫자가_아닌_페이지_조건은_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/conversations/conv_any/turns")
                        .param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/v1/conversations/conv_any/turns")
                        .param("size", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    void 턴이_없거나_페이지를_초과하면_빈_목록을_반환한다() throws Exception {
        conversationSessionRepository.save(ConversationSession.create(
                "conv_empty_turns", 15L, null, ContextType.DAILY, ConversationGoal.DIARY, 6));

        mockMvc.perform(get("/api/v1/conversations/conv_empty_turns/turns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turns").isArray())
                .andExpect(jsonPath("$.turns").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/v1/conversations/conv_empty_turns/turns")
                        .param("page", "3")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(3))
                .andExpect(jsonPath("$.turns").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void 접근할_수_없는_대화의_턴_목록은_403을_반환한다() throws Exception {
        conversationSessionRepository.save(ConversationSession.create(
                "conv_turns_denied", 15L, null, ContextType.DAILY, ConversationGoal.DIARY, 6));
        doThrow(new ConversationException(ConversationErrorCode.CHILD_ACCESS_DENIED))
                .when(childAccessVerifier).verifyAccess(1L, 15L);

        mockMvc.perform(get("/api/v1/conversations/conv_turns_denied/turns"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("CONVERSATION_ACCESS_DENIED"));
    }

    private String dailyRequest(int maxQuestionCount) {
        return """
                {"contextType":"DAILY","gameSessionId":null,
                 "conversationGoal":"DIARY","maxQuestionCount":%d}
                """.formatted(maxQuestionCount);
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedUtcClock() {
            return Clock.fixed(Instant.parse("2026-07-22T01:08:00Z"), ZoneOffset.UTC);
        }
    }
}
