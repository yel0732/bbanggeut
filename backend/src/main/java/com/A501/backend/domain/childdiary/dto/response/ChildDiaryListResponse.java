package com.A501.backend.domain.childdiary.dto.response;

import java.util.List;

public record ChildDiaryListResponse(List<ChildDiaryResponse> items, int page, int size,
        long totalElements, int totalPages) {}
