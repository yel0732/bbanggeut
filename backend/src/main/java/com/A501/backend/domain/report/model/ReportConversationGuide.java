package com.A501.backend.domain.report.model;

import java.util.List;

public record ReportConversationGuide(
        String description,
        List<String> questions
) {
    public ReportConversationGuide {
        questions = questions == null ? List.of() : List.copyOf(questions);
    }

    public static ReportConversationGuide empty() {
        return new ReportConversationGuide("", List.of());
    }
}
