package com.A501.backend.domain.report.dto.response;

import org.springframework.data.domain.Page;

public record ReportPageResponse(
        int number,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    public static ReportPageResponse from(Page<?> page) {
        return new ReportPageResponse(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext());
    }
}
