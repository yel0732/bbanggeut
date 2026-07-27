package com.A501.backend.domain.childdiary.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.A501.backend.domain.childdiary.model.ChildDiaryAiRequest;

class RuleBasedChildDiaryGeneratorTest {
    private final RuleBasedChildDiaryGenerator generator = new RuleBasedChildDiaryGenerator();

    @Test
    void createsThreeOrFourConstrainedLinesForOneTopic() {
        List<String> lines = generator.generate(List.of(topic("DAILY", "친구와 놀이터에서 놀았다", "즐거웠다")));
        assertThat(lines).hasSizeBetween(3, 4).doesNotHaveDuplicates();
        assertThat(lines).allMatch(line -> !line.isBlank() && line.length() <= 40);
    }

    @Test
    void createsTwoGameLinesThenTwoDailyLines() {
        List<String> lines = generator.generate(List.of(
                topic("GAME", "과일 자르기 게임을 했다", "신났다"),
                topic("DAILY", "친구와 놀이터에서 놀았다", "즐거웠다")));
        assertThat(lines).hasSize(4).doesNotHaveDuplicates();
        assertThat(lines.get(0)).contains("게임");
        assertThat(lines.get(2)).contains("친구");
    }

    private ChildDiaryAiRequest.Topic topic(String type, String event, String emotion) {
        return new ChildDiaryAiRequest.Topic(type, new ChildDiaryAiRequest.Slots(
                event, List.of(), null, emotion, null, null));
    }
}
