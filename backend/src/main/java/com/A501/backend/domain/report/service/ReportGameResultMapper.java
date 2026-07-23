package com.A501.backend.domain.report.service;

import org.springframework.stereotype.Component;

import com.A501.backend.domain.report.entity.ReportGameResult;
import com.A501.backend.domain.report.model.ReportGameSource;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class ReportGameResultMapper {

    private final ObjectMapper objectMapper;

    public ReportGameResultMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ReportGameResult toSnapshot(
            Long reportId,
            ReportGameSource source
    ) {
        return ReportGameResult.snapshot(
                reportId,
                source,
                serializeMetrics(source));
    }

    private String serializeMetrics(ReportGameSource source) {
        if (source.resultMetrics() == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(source.resultMetrics());
        } catch (JacksonException exception) {
            throw new IllegalArgumentException(
                    "게임 결과 지표를 JSON으로 변환할 수 없습니다.",
                    exception);
        }
    }
}
