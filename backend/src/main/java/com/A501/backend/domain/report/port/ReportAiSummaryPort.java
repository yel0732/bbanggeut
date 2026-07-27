package com.A501.backend.domain.report.port;

import com.A501.backend.domain.report.model.ReportAiSummaryRequest;
import com.A501.backend.domain.report.model.ReportAiSummaryResponse;

public interface ReportAiSummaryPort {

    ReportAiSummaryResponse generate(ReportAiSummaryRequest request);
}
