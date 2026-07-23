package com.A501.backend.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.A501.backend.domain.report.model.DailyReportCandidate;
import com.A501.backend.domain.report.model.ReportPeriod;
import com.A501.backend.domain.report.repository.DailyReportRepository;

@SpringBootTest(properties = "report.schedule.enabled=false")
class DailyReportRegistrationConcurrencyTest {

    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    @Autowired
    private DailyReportRegistrationService registrationService;

    @Autowired
    private DailyReportRepository dailyReportRepository;

    @BeforeEach
    void setUp() {
        dailyReportRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        executorService.shutdownNow();
    }

    @Test
    void 같은_아이와_리포트_날짜의_동시_등록은_한_건만_성공한다()
            throws Exception {
        DailyReportCandidate candidate = new DailyReportCandidate(
                15L,
                new ReportPeriod(
                        LocalDateTime.of(2026, 7, 22, 19, 0),
                        LocalDateTime.of(2026, 7, 23, 19, 0),
                        LocalDate.of(2026, 7, 23)));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Boolean> first = executorService.submit(() ->
                registerAtTheSameTime(candidate, ready, start));
        Future<Boolean> second = executorService.submit(() ->
                registerAtTheSameTime(candidate, ready, start));

        ready.await();
        start.countDown();

        assertThat(List.of(first.get(), second.get()))
                .containsExactlyInAnyOrder(true, false);
        assertThat(dailyReportRepository.count()).isEqualTo(1);
    }

    private boolean registerAtTheSameTime(
            DailyReportCandidate candidate,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        return registrationService.register(candidate);
    }
}
