package com.A501.backend.domain.childdiary.service;

import java.util.HashSet;
import java.util.List;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.A501.backend.domain.childdiary.model.ChildDiaryAiProperties;
import com.A501.backend.domain.childdiary.model.ChildDiaryAiRequest;
import com.A501.backend.domain.childdiary.model.ChildDiaryAiResponse;
import com.A501.backend.domain.childdiary.port.ChildDiaryAiPort;

@Component
public class HttpChildDiaryAiAdapter implements ChildDiaryAiPort {
    private static final String TITLE = "오늘의 일기";
    private final RestClient restClient;

    public HttpChildDiaryAiAdapter(ChildDiaryAiProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.timeout());
        factory.setReadTimeout(properties.timeout());
        restClient = RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory).build();
    }

    @Override
    public ChildDiaryAiResponse generate(ChildDiaryAiRequest request) {
        try {
            ChildDiaryAiResponse response = restClient.post()
                    .uri("/internal/v1/child-diaries/summary")
                    .body(request).retrieve().body(ChildDiaryAiResponse.class);
            validate(request, response);
            return response;
        } catch (HttpMessageConversionException | RestClientException exception) {
            throw new ChildDiaryAiUnavailableException("Child diary AI request failed.", exception);
        }
    }

    private void validate(ChildDiaryAiRequest request, ChildDiaryAiResponse response) {
        List<String> lines = response == null ? null : response.lines();
        if (response == null || !request.conversationId().equals(response.conversationId())
                || !request.diaryDate().equals(response.diaryDate()) || !TITLE.equals(response.title())
                || lines == null || lines.size() < 3 || lines.size() > 5
                || lines.stream().anyMatch(line -> line == null || line.isBlank() || line.length() > 60)
                || new HashSet<>(lines).size() != lines.size()
                || isBlank(response.modelVersion()) || isBlank(response.promptVersion())) {
            throw new ChildDiaryAiUnavailableException("Child diary AI returned an invalid response.");
        }
    }

    private boolean isBlank(String value) { return value == null || value.isBlank(); }
}
