package com.A501.backend.domain.report.dto.response;

import java.util.List;

public record ReportListResponse(
        Long childId,
        List<ReportListItemResponse> reports,
        ReportPageResponse page
) {
}
