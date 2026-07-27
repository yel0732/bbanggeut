package com.A501.backend.domain.report.service;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.A501.backend.domain.report.model.ReportAiProperties;
import com.A501.backend.domain.report.model.ReportAiSummaryRequest;
import com.A501.backend.domain.report.model.ReportAiSummaryResponse;
import com.A501.backend.domain.report.port.ReportAiSummaryPort;

@Component
public class HttpReportAiSummaryAdapter implements ReportAiSummaryPort {

    private final RestClient restClient;

    public HttpReportAiSummaryAdapter(ReportAiProperties properties) {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeout());
        requestFactory.setReadTimeout(properties.timeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public ReportAiSummaryResponse generate(ReportAiSummaryRequest request) {
        try {
            ReportAiSummaryResponse response = restClient.post()
                    .uri("/internal/v1/reports/daily-summary")
                    .body(request)
                    .retrieve()
                    .body(ReportAiSummaryResponse.class);
            validate(response);
            return response;
        } catch (HttpMessageConversionException | RestClientException exception) {
            throw new ReportAiUnavailableException(
                    "Report AI request failed.", exception);
        }
    }

    private void validate(ReportAiSummaryResponse response) {
        if (response == null
                || isBlank(response.title())
                || response.title().length() > 200
                || isBlank(response.body())
                || response.body().length() > 2000
                || isBlank(response.modelVersion())
                || isBlank(response.promptVersion())) {
            throw new ReportAiUnavailableException(
                    "Report AI returned an invalid response.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
