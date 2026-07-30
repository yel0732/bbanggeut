package com.A501.backend.domain.conversation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

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
import org.mockito.ArgumentCaptor;

import com.A501.backend.domain.conversation.dto.internal.DialogueAiDecision;
import com.A501.backend.domain.conversation.dto.internal.DialogueAiRequest;
import com.A501.backend.domain.conversation.dto.internal.DialogueAiResponse;
import com.A501.backend.domain.conversation.dto.internal.DialogueOpeningResponse;
import com.A501.backend.domain.conversation.dto.internal.DialogueSlotsPayload;
import com.A501.backend.domain.conversation.entity.ContextType;
import com.A501.backend.domain.conversation.entity.ConversationGoal;
import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.ConversationStatus;
import com.A501.backend.domain.conversation.entity.ConversationTopic;
import com.A501.backend.domain.conversation.entity.ConversationTopicState;
import com.A501.backend.domain.conversation.entity.ConversationTopicStatus;
import com.A501.backend.domain.conversation.entity.ConversationTurn;
import com.A501.backend.domain.conversation.entity.Speaker;
import com.A501.backend.domain.conversation.entity.TerminationReason;
import com.A501.backend.domain.conversation.exception.ConversationErrorCode;
import com.A501.backend.domain.conversation.exception.ConversationException;
import com.A501.backend.domain.conversation.repository.ConversationSessionRepository;
import com.A501.backend.domain.conversation.repository.ConversationTopicStateRepository;
import com.A501.backend.domain.conversation.repository.ConversationTurnRepository;
import com.A501.backend.domain.conversation.service.ChildAccessVerifier;
import com.A501.backend.domain.conversation.service.ConversationTextTurnPersistenceService;
import com.A501.backend.domain.conversation.service.DialogueAiClient;
import com.A501.backend.domain.conversation.service.PreparedTextTurn;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ConversationTextTurnIntegrationTest.FixedClockConfig.class)
class ConversationTextTurnIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ConversationSessionRepository sessionRepository;

    @Autowired
    private ConversationTurnRepository turnRepository;

    @Autowired
    private ConversationTopicStateRepository topicRepository;

    @Autowired
    private ConversationTextTurnPersistenceService persistenceService;

    @MockitoBean
    private ChildAccessVerifier childAccessVerifier;

    @MockitoBean
    private DialogueAiClient dialogueAiClient;

    @BeforeEach
    void setUp() {
        reset(dialogueAiClient, childAccessVerifier);
        topicRepository.deleteAll();
        turnRepository.deleteAll();
        sessionRepository.deleteAll();
    }

    @Test
    void 첫_답변과_AI_질문을_원자적으로_저장하고_질문_수를_증가시킨다()
            throws Exception {
        saveReadySession("conv_text_continue");
        DialogueSlotsPayload updatedSlots = new DialogueSlotsPayload(
                "블록 놀이",
                List.of("친구"),
                null,
                "신남",
                null,
                null);
        when(dialogueAiClient.processTurn(any())).thenReturn(
                new DialogueAiResponse(
                        updatedSlots,
                        List.of("memorableMoment"),
                        DialogueAiDecision.CONTINUE,
                        null,
                        "친구와 블록을 만들었구나! 왜 신나는 마음이 들었어?",
                        "왜 신나는 마음이 들었어?",
                        "emotionReason",
                        "generated",
                        "llm",
                        null));

        mockMvc.perform(post(
                        "/api/v1/conversations/conv_text_continue/text-turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"친구와 블록을 만들어서 신났어."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.childTurn.turnSeq").value(1))
                .andExpect(jsonPath("$.assistantTurn.turnSeq").value(2))
                .andExpect(jsonPath("$.assistantTurn.content")
                        .value("친구와 블록을 만들었구나! 왜 신나는 마음이 들었어?"))
                .andExpect(jsonPath("$.conversationState")
                        .value("IN_PROGRESS"));

        ConversationSession session = sessionRepository
                .findById("conv_text_continue")
                .orElseThrow();
        assertThat(session.getStatus()).isEqualTo(
                ConversationStatus.IN_PROGRESS);
        assertThat(session.getQuestionCount()).isEqualTo(1);
        assertThat(session.getStartedAt()).isNotNull();
        assertThat(turnRepository
                .findByConversationIdOrderByTurnSeqAsc(
                        "conv_text_continue",
                        org.springframework.data.domain.PageRequest.of(0, 10))
                .getContent())
                .extracting(turn -> turn.getSpeaker())
                .containsExactly(Speaker.CHILD, Speaker.AI);
        assertThat(turnRepository
                .findTop5ByConversationIdAndSpeakerAndStatusOrderByTurnSeqDesc(
                        "conv_text_continue",
                        Speaker.AI,
                        com.A501.backend.domain.conversation.entity.TurnStatus.COMPLETED)
                .get(0)
                .getQuestionTarget())
                .isEqualTo("emotionReason");
        PreparedTextTurn nextPrepared = persistenceService.prepare(
                "conv_text_continue");
        assertThat(nextPrepared.currentQuestion())
                .isEqualTo("왜 신나는 마음이 들었어?");
        assertThat(nextPrepared.currentTarget())
                .isEqualTo("emotionReason");
        assertThat(nextPrepared.skippedTargets())
                .containsExactly("memorableMoment");
        assertThat(nextPrepared.recentQuestions())
                .containsExactly("왜 신나는 마음이 들었어?");
        assertThat(topicRepository.findByConversationIdAndTopicType(
                        "conv_text_continue",
                        ConversationTopic.DAILY))
                .get()
                .extracting(state -> state.getSlotsJson())
                .asString()
                .contains("블록 놀이");

        mockMvc.perform(get(
                        "/harness/api/conversations/conv_text_continue/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session.status")
                        .value("IN_PROGRESS"))
                .andExpect(jsonPath("$.session.totalQuestionCount").value(1))
                .andExpect(jsonPath("$.slots.event").value("블록 놀이"))
                .andExpect(jsonPath("$.slots.people[0]").value("친구"));
    }

    @Test
    void 필수_슬롯이_채워지면_AI_질문_없이_세션을_완료한다()
            throws Exception {
        saveReadySession("conv_text_terminal");
        DialogueSlotsPayload completedSlots = new DialogueSlotsPayload(
                "놀이",
                List.of(),
                null,
                "기쁨",
                "함께해서",
                "완성 순간");
        when(dialogueAiClient.processTurn(any())).thenReturn(
                new DialogueAiResponse(
                        completedSlots,
                        null,
                        DialogueAiDecision.TERMINATE,
                        TerminationReason.REQUIRED_SLOTS_FILLED,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "오늘 이야기 들려줘서 고마워! 다음에 또 이야기하자."));

        mockMvc.perform(post(
                        "/api/v1/conversations/conv_text_terminal/text-turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"마지막에는 같이 정리했어."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assistantTurn.content")
                        .value("오늘 이야기 들려줘서 고마워! 다음에 또 이야기하자."))
                .andExpect(jsonPath("$.conversationState")
                        .value("COMPLETED"));

        ConversationSession session = sessionRepository
                .findById("conv_text_terminal")
                .orElseThrow();
        assertThat(session.getStatus()).isEqualTo(ConversationStatus.COMPLETED);
        assertThat(session.getTerminationReason()).isEqualTo(
                TerminationReason.REQUIRED_SLOTS_FILLED);
        assertThat(session.getQuestionCount()).isZero();
        assertThat(turnRepository.count()).isEqualTo(1);
    }

    @Test
    void AI가_실패하면_대화_상태와_턴을_변경하지_않는다()
            throws Exception {
        saveReadySession("conv_text_failure");
        doThrow(new ConversationException(
                ConversationErrorCode.AI_DIALOGUE_UNAVAILABLE))
                .when(dialogueAiClient)
                .processTurn(any());

        mockMvc.perform(post(
                        "/api/v1/conversations/conv_text_failure/text-turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"합성 답변"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error")
                        .value("AI_DIALOGUE_UNAVAILABLE"));

        ConversationSession session = sessionRepository
                .findById("conv_text_failure")
                .orElseThrow();
        assertThat(session.getStatus()).isEqualTo(ConversationStatus.READY);
        assertThat(session.getQuestionCount()).isZero();
        assertThat(turnRepository.count()).isZero();
        assertThat(topicRepository.count()).isEqualTo(1);
    }

    @Test
    void 공백_답변은_AI를_호출하지_않고_400을_반환한다()
            throws Exception {
        saveReadySession("conv_text_blank");

        mockMvc.perform(post(
                        "/api/v1/conversations/conv_text_blank/text-turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));

        verifyNoInteractions(dialogueAiClient);
    }

    @Test
    void 로컬_Harness_페이지를_민감정보_없이_제공한다() throws Exception {
        mockMvc.perform(get("/harness"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.TEXT_HTML))
                .andExpect(header().string(
                        "Cache-Control",
                        containsString("no-store")))
                .andExpect(header().string(
                        "Content-Security-Policy",
                        containsString("frame-ancestors 'none'")))
                .andExpect(content().string(
                        containsString("Dialogue Integration Harness")))
                .andExpect(content().string(
                        containsString("data-auto-create-session=\"true\"")))
                .andExpect(content().string(not(containsString("GMS_KEY"))));
        mockMvc.perform(get("/harness"))
                .andExpect(content().string(not(containsString("getUserMedia"))))
                .andExpect(content().string(not(containsString("RTCPeerConnection"))))
                .andExpect(content().string(not(containsString("speechSynthesis"))));
    }

    @Test
    void 로컬_Harness는_활성_세션이_있으면_새로_만들지_않고_재개한다()
            throws Exception {
        saveReadySession("conv_harness_active");
        when(dialogueAiClient.openConversation(any())).thenReturn(
                new DialogueOpeningResponse(
                        "오늘 가장 기억에 남는 일은 무엇이었어?",
                        "오늘 가장 기억에 남는 일은 무엇이었어?",
                        "event"));

        mockMvc.perform(post("/harness/api/children/15/conversations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId")
                        .value("conv_harness_active"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.totalQuestionCount").value(1));

        assertThat(sessionRepository.count()).isEqualTo(1);
        assertThat(turnRepository.findByConversationIdAndTurnSeq(
                        "conv_harness_active",
                        1))
                .get()
                .extracting(ConversationTurn::getContent)
                .isEqualTo("오늘 가장 기억에 남는 일은 무엇이었어?");

        when(dialogueAiClient.processTurn(any())).thenReturn(
                new DialogueAiResponse(
                        DialogueSlotsPayload.empty(),
                        DialogueAiDecision.CONTINUE,
                        null,
                        "친구와 놀았구나! 그때 어떤 기분이었어?",
                        "그때 어떤 기분이었어?",
                        "emotion",
                        "generated",
                        "llm",
                        null));

        mockMvc.perform(post(
                        "/api/v1/conversations/conv_harness_active/text-turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"친구와 놀았어.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.childTurn.turnSeq").value(2))
                .andExpect(jsonPath("$.assistantTurn.turnSeq").value(3));

        ArgumentCaptor<DialogueAiRequest> requestCaptor =
                ArgumentCaptor.forClass(DialogueAiRequest.class);
        verify(dialogueAiClient).processTurn(requestCaptor.capture());
        assertThat(requestCaptor.getValue().currentQuestion())
                .isEqualTo("오늘 가장 기억에 남는 일은 무엇이었어?");
        assertThat(requestCaptor.getValue().currentTarget())
                .isEqualTo("event");
        assertThat(requestCaptor.getValue().questionCount()).isEqualTo(1);

        mockMvc.perform(post("/harness/api/children/15/conversations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalQuestionCount").value(2));
        verify(dialogueAiClient, times(1)).openConversation(any());
    }

    @Test
    void AI_처리_중_세션이_변경되면_결과를_저장하지_않는다() {
        saveReadySession("conv_text_conflict");
        PreparedTextTurn prepared = persistenceService.prepare(
                "conv_text_conflict");

        ConversationSession changedSession = sessionRepository
                .findById("conv_text_conflict")
                .orElseThrow();
        changedSession.start(LocalDateTime.of(2026, 7, 22, 1, 7));
        sessionRepository.saveAndFlush(changedSession);

        DialogueAiResponse response = new DialogueAiResponse(
                DialogueSlotsPayload.empty(),
                DialogueAiDecision.CONTINUE,
                null,
                "오늘 가장 기억에 남는 일은 뭐였어?",
                "generated",
                "llm",
                null);

        assertThatThrownBy(() -> persistenceService.finalizeTurn(
                        prepared,
                        "친구와 놀았어.",
                        response))
                .isInstanceOfSatisfying(
                        ConversationException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ConversationErrorCode
                                        .CONVERSATION_TURN_CONFLICT));
        assertThat(turnRepository.count()).isZero();
        assertThat(topicRepository.count()).isEqualTo(1);
    }

    @Test
    void GAME_주제가_끝나면_DAILY_질문_수를_0부터_새로_계산한다()
            throws Exception {
        saveGameAndDailySession("conv_topic_switch");
        when(dialogueAiClient.processTurn(any())).thenReturn(
                new DialogueAiResponse(
                        DialogueSlotsPayload.empty(),
                        DialogueAiDecision.SWITCH_TOPIC,
                        TerminationReason.MAX_QUESTION_REACHED,
                        "게임 이야기를 들려줘서 고마워! 이번에는 오늘 있었던 다른 일도 이야기해 줄래?",
                        "이번에는 오늘 있었던 다른 일도 이야기해 줄래?",
                        "fallback",
                        "policy",
                        null));

        mockMvc.perform(post(
                        "/api/v1/conversations/conv_topic_switch/text-turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"게임은 여기까지 이야기할래.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.childTurn.topicType").value("GAME"))
                .andExpect(jsonPath("$.assistantTurn.topicType").value("DAILY"))
                .andExpect(jsonPath("$.conversationState").value("IN_PROGRESS"));

        ConversationSession session =
                sessionRepository.findById("conv_topic_switch").orElseThrow();
        assertThat(session.getCurrentTopic()).isEqualTo(ConversationTopic.DAILY);
        assertThat(session.getQuestionCount()).isEqualTo(1);

        List<ConversationTopicState> topics =
                topicRepository.findAllByConversationIdOrderByTopicOrderAsc(
                        "conv_topic_switch");
        assertThat(topics.get(0).getStatus())
                .isEqualTo(ConversationTopicStatus.COMPLETED);
        assertThat(topics.get(0).getQuestionCount()).isZero();
        assertThat(topics.get(1).getStatus())
                .isEqualTo(ConversationTopicStatus.IN_PROGRESS);
        assertThat(topics.get(1).getQuestionCount()).isEqualTo(1);
    }

    private void saveReadySession(String conversationId) {
        sessionRepository.saveAndFlush(
                ConversationSession.create(
                        conversationId,
                        15L,
                        null,
                        ContextType.DAILY,
                        ConversationGoal.DIARY,
                        6));
        topicRepository.saveAndFlush(ConversationTopicState.create(
                "topic_" + conversationId,
                conversationId,
                ConversationTopic.DAILY,
                1,
                6,
                """
                {"event":null,"people":[],"place":null,"emotion":null,
                 "emotionReason":null,"memorableMoment":null}
                """,
                """
                {"topic":"DAILY","games":[]}
                """));
    }

    private void saveGameAndDailySession(String conversationId) {
        sessionRepository.saveAndFlush(ConversationSession.create(
                conversationId,
                15L,
                ConversationTopic.GAME,
                12));
        topicRepository.saveAllAndFlush(List.of(
                ConversationTopicState.create(
                        "topic_game_" + conversationId,
                        conversationId,
                        ConversationTopic.GAME,
                        1,
                        6,
                        "{}",
                        "{\"topic\":\"GAME\",\"games\":[]}"),
                ConversationTopicState.create(
                        "topic_daily_" + conversationId,
                        conversationId,
                        ConversationTopic.DAILY,
                        2,
                        6,
                        "{}",
                        "{\"topic\":\"DAILY\",\"games\":[]}")));
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
