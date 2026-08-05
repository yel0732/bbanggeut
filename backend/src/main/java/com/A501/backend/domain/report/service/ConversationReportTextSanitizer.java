package com.A501.backend.domain.report.service;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
class ConversationReportTextSanitizer {

    private static final String PRIVATE_DETAIL_PLACEHOLDER =
            "개인적인 자세한 내용은 리포트 입력에서 제외했습니다.";
    private static final List<String> SAFETY_CUES = List.of(
            "죽고 싶",
            "죽고싶",
            "죽어도",
            "죽을래",
            "사라지고 싶",
            "사라지고싶",
            "살기 싫",
            "살기싫");
    private static final List<String> PRIVATE_DETAIL_CUES = List.of(
            "유치원",
            "어린이집",
            "선생님",
            "회사",
            "다닌대",
            "다녀",
            "삼성",
            "변호사",
            "의사",
            "경찰");

    String sanitizeForAi(String value) {
        if (!hasText(value)) {
            return "";
        }
        String normalized = value.trim();
        if (containsSafetySignal(normalized)) {
            return "";
        }
        if (containsPrivateDetail(normalized)) {
            return PRIVATE_DETAIL_PLACEHOLDER;
        }
        return normalized;
    }

    boolean canUseAsQuote(String value) {
        return hasText(value)
                && !containsSafetySignal(value)
                && !containsPrivateDetail(value);
    }

    boolean containsSafetySignal(String value) {
        String normalized = normalize(value);
        return SAFETY_CUES.stream().anyMatch(normalized::contains);
    }

    boolean containsPrivateDetail(String value) {
        String normalized = normalize(value);
        return PRIVATE_DETAIL_CUES.stream().anyMatch(normalized::contains);
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
