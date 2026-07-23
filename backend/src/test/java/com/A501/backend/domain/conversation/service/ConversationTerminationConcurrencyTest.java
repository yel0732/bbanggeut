package com.A501.backend.domain.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.A501.backend.domain.conversation.dto.request.CompleteConversationRequest;
import com.A501.backend.domain.conversation.dto.response.CompleteConversationResponse;
import com.A501.backend.domain.conversation.entity.ContextType;
import com.A501.backend.domain.conversation.entity.ConversationGoal;
import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.TerminationReason;
import com.A501.backend.domain.conversation.repository.ConversationSessionRepository;

@SpringBootTest
class ConversationTerminationConcurrencyTest {

    private static final String CONVERSATION_ID = "conv_concurrency_001";

    @Autowired
    private ConversationSessionService conversationSessionService;

    @Autowired
    private ConversationSessionRepository conversationSessionRepository;

    @MockitoSpyBean
    private ConversationTerminationExecutor conversationTerminationExecutor;

    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        conversationSessionRepository.deleteAll();
        ConversationSession session = ConversationSession.create(
                CONVERSATION_ID, 15L, null, ContextType.DAILY, ConversationGoal.DIARY, 6);
        session.start();
        conversationSessionRepository.saveAndFlush(session);
        executorService = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        executorService.shutdownNow();
    }

    @Test
    void 동시_종료_요청은_실제_트랜잭션_충돌_후_동일한_최초_결과를_반환한다() throws Exception {
        CountDownLatch bothTerminationTransactionsLoaded = new CountDownLatch(2);
        AtomicInteger terminationAttemptCount = new AtomicInteger();

        doAnswer(invocation -> {
            String conversationId = invocation.getArgument(0);
            TerminationReason reason = invocation.getArgument(1);
            ConversationSession session = conversationSessionRepository.findById(conversationId).orElseThrow();

            if (terminationAttemptCount.incrementAndGet() <= 2) {
                bothTerminationTransactionsLoaded.countDown();
                assertThat(bothTerminationTransactionsLoaded.await(5, TimeUnit.SECONDS)).isTrue();
            }

            session.terminate(
                    LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS),
                    reason);
            return conversationSessionRepository.saveAndFlush(session);
        }).when(conversationTerminationExecutor).terminate(eq(CONVERSATION_ID), any());

        Future<CompleteConversationResponse> userFinished = executorService.submit(
                () -> conversationSessionService.completeConversation(
                        CONVERSATION_ID, new CompleteConversationRequest("USER_FINISHED")));
        Future<CompleteConversationResponse> connectionLost = executorService.submit(
                () -> conversationSessionService.completeConversation(
                        CONVERSATION_ID, new CompleteConversationRequest("CONNECTION_LOST")));

        CompleteConversationResponse firstResponse = userFinished.get(10, TimeUnit.SECONDS);
        CompleteConversationResponse secondResponse = connectionLost.get(10, TimeUnit.SECONDS);

        assertThat(secondResponse.status()).isEqualTo(firstResponse.status());
        assertThat(secondResponse.terminationReason()).isEqualTo(firstResponse.terminationReason());
        assertThat(secondResponse.completedAt()).isEqualTo(firstResponse.completedAt());

        ConversationSession stored = conversationSessionRepository.findById(CONVERSATION_ID).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(firstResponse.status());
        assertThat(stored.getTerminationReason()).isEqualTo(firstResponse.terminationReason());
        assertThat(stored.getVersion()).isEqualTo(1L);
    }
}
