package com.A501.backend.domain.childdiary.dto.response;

import java.time.LocalDate;
import java.util.List;

public record ChildDiaryPreviewResponse(String conversationId, LocalDate diaryDate,
        String title, List<String> lines, String previewToken) {}
