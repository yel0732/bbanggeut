package com.A501.backend.domain.report.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.A501.backend.domain.report.dto.response.ReportGameResultResponse;
import com.A501.backend.domain.report.dto.response.ReportGameSummaryResponse;

@Component
public class ReportGameSummaryService {

    private static final Map<String, Set<String>> SUMMABLE_METRICS = Map.of(
            "DOBBLE", Set.of(
                    "solvedCardCount",
                    "perfectCount",
                    "excellentCount",
                    "goodCount"),
            "FRUIT_NINJA", Set.of(
                    "totalFruitCount",
                    "slicedFruitCount"),
            "RECYCLING", Set.of(
                    "successfulCount",
                    "wrongCount"),
            "WORD_MATCH", Set.of(
                    "solvedWordCount",
                    "incompleteWordCount",
                    "wrongCount",
                    "hintUsedCount"));

    public List<ReportGameSummaryResponse> summarize(
            List<ReportGameResultResponse> gameResults) {
        Map<String, SummaryAccumulator> summaries = new LinkedHashMap<>();
        for (ReportGameResultResponse result : gameResults) {
            summaries.computeIfAbsent(
                            result.gameType(),
                            ignored -> new SummaryAccumulator(
                                    result.gameType(),
                                    result.gameName()))
                    .add(result);
        }
        return summaries.values().stream()
                .map(SummaryAccumulator::toResponse)
                .toList();
    }

    private static final class SummaryAccumulator {

        private final String gameType;
        private final String gameName;
        private final Map<String, Long> metrics = new LinkedHashMap<>();
        private int playCount;
        private long totalPlayDurationSeconds;
        private int highestScore;

        private SummaryAccumulator(String gameType, String gameName) {
            this.gameType = gameType;
            this.gameName = gameName;
        }

        private void add(ReportGameResultResponse result) {
            playCount++;
            totalPlayDurationSeconds += result.playDurationSeconds();
            highestScore = Math.max(highestScore, result.score());
            addMetrics(result.resultMetrics());
        }

        private void addMetrics(Object resultMetrics) {
            if (!(resultMetrics instanceof Map<?, ?> values)) {
                return;
            }
            Set<String> allowedKeys = SUMMABLE_METRICS.getOrDefault(
                    gameType,
                    Set.of());
            for (String key : allowedKeys) {
                Object value = values.get(key);
                if (value instanceof Number number) {
                    metrics.merge(key, number.longValue(), Long::sum);
                }
            }
        }

        private ReportGameSummaryResponse toResponse() {
            return new ReportGameSummaryResponse(
                    gameType,
                    gameName,
                    playCount,
                    totalPlayDurationSeconds,
                    highestScore,
                    metrics);
        }
    }
}
