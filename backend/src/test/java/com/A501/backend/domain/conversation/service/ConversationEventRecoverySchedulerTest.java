package com.A501.backend.domain.conversation.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import com.A501.backend.domain.conversation.dto.stream.ConversationStreamDelivery;
import com.A501.backend.domain.conversation.dto.stream.ConversationStreamPendingMessage;

@ExtendWith(MockitoExtension.class)
class ConversationEventRecoverySchedulerTest {

    @Mock
    private ConversationStreamClient streamClient;

    @Mock
    private ConversationEventProcessor eventProcessor;

    @Mock
    private ConversationEventFailureService failureService;

    private ConversationEventRecoveryScheduler scheduler;

    private ConversationStreamProperties.Recovery recovery;

    @BeforeEach
    void setUp() {
        recovery = new ConversationStreamProperties.Recovery(
                true,
                Duration.ofSeconds(30),
                Duration.ofSeconds(10),
                Duration.ofSeconds(30),
                3,
                100);
        ConversationStreamProperties properties = new ConversationStreamProperties(
                "conversation-events",
                "spring-conversation-consumers",
                true,
                Duration.ofSeconds(1),
                20,
                recovery);
        scheduler = new ConversationEventRecoveryScheduler(
                streamClient,
                eventProcessor,
                failureService,
                properties);
    }

    @Test
    void keepsFailedClaimPendingBeforeRetryLimit() {
        ConversationStreamPendingMessage pending =
                new ConversationStreamPendingMessage("2000-0", 1);
        ConversationStreamDelivery claimed =
                new ConversationStreamDelivery("2000-0", "{}", 2);
        when(streamClient.findPending(100, recovery.minIdle()))
                .thenReturn(List.of(pending));
        when(streamClient.claim(anyString(), org.mockito.ArgumentMatchers.eq(List.of(pending)), org.mockito.ArgumentMatchers.eq(recovery.minIdle())))
                .thenReturn(List.of(claimed));
        when(eventProcessor.process(claimed))
                .thenThrow(new ConversationEventContractException("invalid"));

        scheduler.recover();

        verify(failureService, never()).recordFailure(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(streamClient, never()).acknowledge("2000-0");
    }

    @Test
    void recordsFailureThenAcknowledgesAtRetryLimit() {
        ConversationStreamPendingMessage pending =
                new ConversationStreamPendingMessage("2000-1", 2);
        ConversationStreamDelivery claimed =
                new ConversationStreamDelivery("2000-1", "{}", 3);
        ConversationEventContractException failure =
                new ConversationEventContractException("invalid");
        when(streamClient.findPending(100, recovery.minIdle()))
                .thenReturn(List.of(pending));
        when(streamClient.claim(anyString(), org.mockito.ArgumentMatchers.eq(List.of(pending)), org.mockito.ArgumentMatchers.eq(recovery.minIdle())))
                .thenReturn(List.of(claimed));
        when(eventProcessor.process(claimed)).thenThrow(failure);

        scheduler.recover();

        InOrder order = inOrder(failureService, streamClient);
        order.verify(failureService).recordFailure(claimed, failure);
        order.verify(streamClient).acknowledge("2000-1");
    }

    @Test
    void doesNotRecordProcessingFailureWhenOnlyAcknowledgeFails() {
        ConversationStreamPendingMessage pending =
                new ConversationStreamPendingMessage("2000-2", 2);
        ConversationStreamDelivery claimed =
                new ConversationStreamDelivery("2000-2", "{}", 3);
        when(streamClient.findPending(100, recovery.minIdle()))
                .thenReturn(List.of(pending));
        when(streamClient.claim(anyString(), org.mockito.ArgumentMatchers.eq(List.of(pending)), org.mockito.ArgumentMatchers.eq(recovery.minIdle())))
                .thenReturn(List.of(claimed));
        when(eventProcessor.process(claimed))
                .thenReturn(ConversationEventProcessingOutcome.DUPLICATE);
        doThrow(new DataAccessResourceFailureException("Redis unavailable"))
                .when(streamClient)
                .acknowledge("2000-2");

        assertThatThrownBy(() -> scheduler.recover())
                .isInstanceOf(DataAccessResourceFailureException.class);

        verify(failureService, never()).recordFailure(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }
}
