package com.A501.backend.domain.childdiary.model;

import java.time.LocalDate;
import java.util.List;

public record ChildDiaryAiRequest(String conversationId, LocalDate diaryDate, List<Topic> topics) {
    public record Topic(String topicType, Slots slots) {}
    public record Slots(String event, List<String> people, String place, String emotion,
            String emotionReason, String memorableMoment) {}
}
