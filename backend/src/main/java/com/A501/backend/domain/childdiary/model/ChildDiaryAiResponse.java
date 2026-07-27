package com.A501.backend.domain.childdiary.model;

import java.time.LocalDate;
import java.util.List;

public record ChildDiaryAiResponse(String conversationId, LocalDate diaryDate, String title,
        List<String> lines, String modelVersion, String promptVersion) {}
