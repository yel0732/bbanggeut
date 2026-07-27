package com.A501.backend.domain.childdiary.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.stereotype.Component;

import com.A501.backend.domain.childdiary.model.ChildDiaryAiRequest;

@Component
public class RuleBasedChildDiaryGenerator {
    public List<String> generate(List<ChildDiaryAiRequest.Topic> topics) {
        int perTopic = topics.size() == 2 ? 2 : 4;
        List<String> result = new ArrayList<>();
        for (ChildDiaryAiRequest.Topic topic : topics) {
            result.addAll(lines(topic.topicType(), topic.slots(), perTopic));
        }
        while (result.size() < 3) {
            String supplement = result.size() == 1 ? "그 순간을 떠올리며 이야기했다."
                    : "오늘 나눈 이야기가 기억에 남았다.";
            if (!result.contains(supplement)) result.add(supplement);
        }
        return List.copyOf(result.subList(0, Math.min(result.size(), topics.size() == 2 ? 4 : 4)));
    }

    private List<String> lines(String topicType, ChildDiaryAiRequest.Slots slots, int limit) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        add(values, sentence(slots.event()));
        add(values, sentence(slots.memorableMoment()));
        if (text(slots.emotion())) {
            String emotion = text(slots.emotionReason())
                    ? slots.emotionReason() + " 때문에 " + slots.emotion() : slots.emotion();
            add(values, sentence(emotion));
        }
        if (text(slots.place())) add(values, sentence(slots.place() + "에서 있었던 일이다"));
        if (slots.people() != null && !slots.people().isEmpty()) {
            add(values, sentence(String.join(", ", slots.people()) + "와 함께했다"));
        }
        List<String> result = new ArrayList<>(values);
        while (result.size() < limit) {
            String subject = "GAME".equals(topicType) ? "게임" : "오늘 하루";
            String supplement = result.size() % 2 == 0 ? subject + "의 순간을 떠올려 보았다."
                    : subject + " 이야기가 기억에 남았다.";
            if (result.contains(supplement)) break;
            result.add(supplement);
        }
        return result.subList(0, Math.min(limit, result.size()));
    }

    private void add(LinkedHashSet<String> values, String value) {
        if (value != null && !value.isBlank()) values.add(value.length() <= 40 ? value : value.substring(0, 39) + ".");
    }
    private String sentence(String value) {
        if (!text(value)) return null;
        String trimmed = value.trim();
        return trimmed.matches(".*[.!?。]$") ? trimmed : trimmed + ".";
    }
    private boolean text(String value) { return value != null && !value.isBlank(); }
}
