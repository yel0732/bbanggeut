package com.A501.backend.domain.childdiary.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ChildDiaryResponse(Long childDiaryId, Long childId, String conversationId,
        LocalDate diaryDate, String title, List<String> lines, LocalDateTime createdAt) {}
