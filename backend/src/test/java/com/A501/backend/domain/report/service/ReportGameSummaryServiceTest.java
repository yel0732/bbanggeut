package com.A501.backend.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.A501.backend.domain.report.dto.response.ReportGameResultResponse;
import com.A501.backend.domain.report.dto.response.ReportGameSummaryResponse;

class ReportGameSummaryServiceTest {

    private final ReportGameSummaryService service =
            new ReportGameSummaryService();

    @Test
    void summarizesOnlyConfiguredReportMetricsAndHighestScore() {
        List<ReportGameSummaryResponse> summaries = service.summarize(List.of(
                game("FRUIT_NINJA", "과일 자르기", 60, 1000, Map.of(
                        "totalFruitCount", 20,
                        "slicedFruitCount", 15,
                        "rulesVersion", "v1")),
                game("FRUIT_NINJA", "과일 자르기", 50, 1400, Map.of(
                        "totalFruitCount", 30,
                        "slicedFruitCount", 25,
                        "rulesVersion", "v1")),
                game("DOBBLE", "그림 맞추기", 120, 3000, Map.of(
                        "solvedCardCount", 4,
                        "perfectCount", 1,
                        "excellentCount", 2,
                        "goodCount", 1,
                        "incompleteCardCount", 1)),
                game("DOBBLE", "그림 맞추기", 100, 3500, Map.of(
                        "solvedCardCount", 5,
                        "perfectCount", 2,
                        "excellentCount", 2,
                        "goodCount", 1,
                        "incompleteCardCount", 0)),
                game("WORD_MATCH", "낱말 맞추기", 90, 1700, Map.of(
                        "solvedWordCount", 2,
                        "incompleteWordCount", 1,
                        "wrongCount", 3,
                        "hintUsedCount", 1)),
                game("RECYCLING", "숲 속 청소하기", 60, 900, Map.of(
                        "successfulCount", 9,
                        "wrongCount", 2))));

        assertThat(summaries).hasSize(4);

        ReportGameSummaryResponse fruit = summaries.get(0);
        assertThat(fruit.playCount()).isEqualTo(2);
        assertThat(fruit.totalPlayDurationSeconds()).isEqualTo(110);
        assertThat(fruit.highestScore()).isEqualTo(1400);
        assertThat(fruit.resultMetrics()).containsExactlyInAnyOrderEntriesOf(
                Map.of("totalFruitCount", 50L, "slicedFruitCount", 40L));

        ReportGameSummaryResponse dobble = summaries.get(1);
        assertThat(dobble.playCount()).isEqualTo(2);
        assertThat(dobble.totalPlayDurationSeconds()).isEqualTo(220);
        assertThat(dobble.highestScore()).isEqualTo(3500);
        assertThat(dobble.resultMetrics()).containsExactlyInAnyOrderEntriesOf(
                Map.of(
                        "solvedCardCount", 9L,
                        "perfectCount", 3L,
                        "excellentCount", 4L,
                        "goodCount", 2L));
        assertThat(dobble.resultMetrics())
                .doesNotContainKey("incompleteCardCount");

        assertThat(summaries.get(2).resultMetrics())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "solvedWordCount", 2L,
                        "incompleteWordCount", 1L,
                        "wrongCount", 3L,
                        "hintUsedCount", 1L));
        assertThat(summaries.get(3).resultMetrics())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "successfulCount", 9L,
                        "wrongCount", 2L));
    }

    private ReportGameResultResponse game(
            String gameType,
            String gameName,
            int duration,
            int score,
            Map<String, Object> metrics) {
        return new ReportGameResultResponse(
                UUID.randomUUID(),
                gameType,
                gameName,
                duration,
                score,
                metrics,
                LocalDateTime.of(2026, 8, 3, 10, 0));
    }
}
