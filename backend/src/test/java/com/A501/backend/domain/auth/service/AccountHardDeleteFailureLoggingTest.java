package com.A501.backend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.A501.backend.domain.auth.security.AccountPurgeProperties;
import com.A501.backend.domain.child.service.ChildPurgeService;
import com.A501.backend.domain.childdiary.service.ChildDiaryPurgeService;
import com.A501.backend.domain.conversation.service.ConversationPurgeService;
import com.A501.backend.domain.game.service.GamePurgeService;
import com.A501.backend.domain.report.service.ReportPurgeService;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * 영구 실패 계정 하나가 로그를 무한히 채우면, 디스크보다 <b>다른 에러가 묻히는 것</b>이 문제다.
 * 임계치 이후 ERROR가 멈추는지 로그 자체로 확인한다.
 */
class AccountHardDeleteFailureLoggingTest {

    private static final Long PARENT_ID = 7L;

    private final AuthPurgeService authPurgeService = mock(AuthPurgeService.class);
    private final ProfilePurgeService profilePurgeService = mock(ProfilePurgeService.class);
    private final ChildPurgeService childPurgeService = mock(ChildPurgeService.class);
    private final ReportPurgeService reportPurgeService = mock(ReportPurgeService.class);
    private final ChildDiaryPurgeService childDiaryPurgeService = mock(ChildDiaryPurgeService.class);
    private final ConversationPurgeService conversationPurgeService =
            mock(ConversationPurgeService.class);
    private final GamePurgeService gamePurgeService = mock(GamePurgeService.class);

    private AccountHardDeleteService service;
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        service = new AccountHardDeleteService(
                new AccountPurgeProperties(Duration.ZERO, 50),
                authPurgeService, profilePurgeService, childPurgeService,
                reportPurgeService, childDiaryPurgeService, conversationPurgeService,
                gamePurgeService);

        appender = new ListAppender<>();
        appender.start();
        logger = (Logger) LoggerFactory.getLogger(AccountHardDeleteService.class);
        logger.addAppender(appender);

        when(authPurgeService.findWithdrawnParentIdsForPurge(any(), anyInt()))
                .thenReturn(List.of(PARENT_ID));
        when(authPurgeService.isWithdrawn(PARENT_ID)).thenReturn(true);
        when(childPurgeService.findChildIdsIncludingDeleted(PARENT_ID)).thenReturn(List.of());
        doThrow(new IllegalStateException("permanent failure"))
                .when(authPurgeService).hardDeleteParent(PARENT_ID);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    void stopsLoggingErrorsOnceTheFailureThresholdIsPassed() {
        int cycles = AccountHardDeleteService.FAILURE_LOG_THRESHOLD + 5;
        for (int i = 0; i < cycles; i++) {
            service.purgeBatch();
        }

        long errors = countAtLeast(Level.ERROR);
        long warns = countAtLeast(Level.WARN) - errors;

        // 임계치까지만 ERROR로 남고, 이후 5회는 WARN으로 내려간다.
        assertThat(errors).isEqualTo(AccountHardDeleteService.FAILURE_LOG_THRESHOLD);
        assertThat(warns).isEqualTo(5);
    }

    // 일시적 장애(예: Redis 재시작)로 실패했다가 복구되면, 다음 실패는 다시 처음부터 세어야 한다.
    @Test
    void resetsTheCounterAfterASuccessfulPurge() {
        service.purgeBatch();
        service.purgeBatch();
        assertThat(countAtLeast(Level.ERROR)).isEqualTo(2);

        doNothing().when(authPurgeService).hardDeleteParent(anyLong());
        service.purgeBatch();

        doThrow(new IllegalStateException("again")).when(authPurgeService).hardDeleteParent(PARENT_ID);
        service.purgeBatch();

        // 카운터가 리셋됐으면 3번째 ERROR가 남는다. 리셋되지 않으면 WARN으로 빠져 2에 머문다.
        assertThat(countAtLeast(Level.ERROR)).isEqualTo(3);
    }

    private long countAtLeast(Level level) {
        return appender.list.stream()
                .filter(event -> event.getLevel().isGreaterOrEqual(level))
                .count();
    }
}
