package com.A501.backend.domain.report.service;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
class ConversationReportTextSanitizer {

    private static final List<String> SAFETY_CUES = List.of(
            "죽고 싶",
            "죽고싶",
            "죽어도",
            "죽을래",
            "사라지고 싶",
            "사라지고싶",
            "살기 싫",
            "살기싫");

    String sanitizeForAi(String value) {
        if (!hasText(value)) {
            return "";
        }
        String normalized = value.trim();
        if (containsSafetySignal(normalized)) {
            return "";
        }
        return normalized;
    }

    boolean canUseAsQuote(String value) {
        return hasText(value) && !containsSafetySignal(value);
    }

    boolean containsSafetySignal(String value) {
        String normalized = normalize(value);
        return SAFETY_CUES.stream().anyMatch(normalized::contains);
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.replaceAll("\\s+", "").toLowerCase();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
