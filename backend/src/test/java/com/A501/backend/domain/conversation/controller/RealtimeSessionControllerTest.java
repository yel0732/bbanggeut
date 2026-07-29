package com.A501.backend.domain.conversation.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.ConversationTopic;
import com.A501.backend.domain.conversation.entity.TerminationReason;
import com.A501.backend.domain.conversation.exception.ConversationErrorCode;
import com.A501.backend.domain.conversation.exception.ConversationException;
import com.A501.backend.domain.conversation.repository.ConversationGameSessionRepository;
import com.A501.backend.domain.conversation.repository.ConversationSessionRepository;
import com.A501.backend.domain.conversation.repository.ConversationTopicStateRepository;
import com.A501.backend.domain.conversation.repository.ConversationTurnRepository;
import com.A501.backend.domain.conversation.service.ChildAccessVerifier;
import com.A501.backend.domain.conversation.dto.realtime.RealtimeChildContext;
import com.A501.backend.domain.conversation.service.RealtimeChildProfileProvider;
import com.A501.backend.domain.conversation.service.RealtimeContextStore;
import com.A501.backend.domain.conversation.service.RealtimeSessionLeaseStore;
import com.A501.backend.domain.conversation.service.RealtimeTicketStore;

@SpringBootTest
@AutoConfigureMockMvc
@Import(RealtimeSessionControllerTest.FixedClockConfig.class)
class RealtimeSessionControllerTest {

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

    @MockitoBean
    private RealtimeContextStore realtimeContextStore;

    @MockitoBean
    private RealtimeTicketStore realtimeTicketStore;

    @MockitoBean
    private RealtimeSessionLeaseStore realtimeSessionLeaseStore;

    // 아이 프로필은 child 테이블에서 읽는다. 이 테스트는 대화 세션 행만 심으므로
    // 실물을 쓰면 CHILD_NOT_FOUND가 난다 — 발급 경로만 보기 위해 가명으로 대체한다.
    @MockitoBean
    private RealtimeChildProfileProvider realtimeChildProfileProvider;

    @BeforeEach
    void setUp() {
        turnRepository.deleteAll();
        gameLinkRepository.deleteAll();
        topicRepository.deleteAll();
        sessionRepository.deleteAll();
        when(realtimeChildProfileProvider.getProfile(anyLong()))
                .thenReturn(new RealtimeChildContext("아이"));
    }

    @Test
    void 정상_세션에_Realtime_Session을_발급한다() throws Exception {
        ConversationSession session = ConversationSession.create(
                "conv_realtime_ok", 15L, ConversationTopic.DAILY, 6);
        sessionRepository.save(session);

        mockMvc.perform(post("/api/v1/conversations/{conversationId}/realtime-session", "conv_realtime_ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value("conv_realtime_ok"))
                .andExpect(jsonPath("$.realtimeSessionId").value(
                        org.hamcrest.Matchers.matchesPattern("rt_[0-9a-f]{32}")))
                .andExpect(jsonPath("$.sessionEpoch").value(1))
                .andExpect(jsonPath("$.signalingUrl").value(
                        "ws://localhost:8000/realtime/signaling"))
                .andExpect(jsonPath("$.realtimeTicket").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").value(
                        org.hamcrest.Matchers.matchesPattern(
                                "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?\\+09:00")))
                .andExpect(jsonPath("$.iceServers").isArray());
    }

    @Test
    void 존재하지_않는_세션은_404를_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/conversations/{conversationId}/realtime-session", "conv_missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CONVERSATION_NOT_FOUND"));
    }

    @Test
    void 자녀_접근_권한이_없으면_403을_반환한다() throws Exception {
        ConversationSession session = ConversationSession.create(
                "conv_realtime_denied", 15L, ConversationTopic.DAILY, 6);
        sessionRepository.save(session);
        doThrow(new ConversationException(ConversationErrorCode.CHILD_ACCESS_DENIED))
                .when(childAccessVerifier)
                .verifyAccess(1L, 15L);

        mockMvc.perform(post("/api/v1/conversations/{conversationId}/realtime-session", "conv_realtime_denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("CONVERSATION_ACCESS_DENIED"));
    }

    @Test
    void 종료된_세션은_409를_반환한다() throws Exception {
        ConversationSession session = ConversationSession.create(
                "conv_realtime_terminal", 15L, ConversationTopic.DAILY, 6);
        session.start();
        session.terminate(LocalDateTime.of(2026, 7, 22, 1, 0), TerminationReason.USER_FINISHED);
        sessionRepository.save(session);

        mockMvc.perform(post("/api/v1/conversations/{conversationId}/realtime-session", "conv_realtime_terminal"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_SESSION_STATE"));
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
