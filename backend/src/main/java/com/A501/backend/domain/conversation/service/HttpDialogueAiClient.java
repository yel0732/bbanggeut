package com.A501.backend.domain.conversation.service;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.A501.backend.domain.conversation.dto.internal.DialogueAiRequest;
import com.A501.backend.domain.conversation.dto.internal.DialogueAiResponse;
import com.A501.backend.domain.conversation.dto.internal.DialogueOpeningRequest;
import com.A501.backend.domain.conversation.dto.internal.DialogueOpeningResponse;
import com.A501.backend.domain.conversation.exception.ConversationErrorCode;
import com.A501.backend.domain.conversation.exception.ConversationException;

@Component
public class HttpDialogueAiClient implements DialogueAiClient {

    private final RestClient restClient;

    public HttpDialogueAiClient(DialogueAiProperties properties) {
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
    public DialogueOpeningResponse openConversation(DialogueOpeningRequest request) {
        return post(
                "/internal/v1/dialogue/opening",
                request,
                DialogueOpeningResponse.class);
    }

    @Override
    public DialogueAiResponse processTurn(DialogueAiRequest request) {
        return post(
                "/internal/v1/dialogue/turns",
                request,
                DialogueAiResponse.class);
    }

    private <T> T post(String uri, Object request, Class<T> responseType) {
        try {
            T response = restClient.post()
                    .uri(uri)
                    .body(request)
                    .retrieve()
                    .body(responseType);
            if (response == null) {
                throw new ConversationException(
                        ConversationErrorCode.AI_DIALOGUE_INVALID_RESPONSE);
            }
            return response;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is5xxServerError()) {
                throw new ConversationException(
                        ConversationErrorCode.AI_DIALOGUE_UNAVAILABLE);
            }
            throw new ConversationException(
                    ConversationErrorCode.AI_DIALOGUE_INVALID_RESPONSE);
        } catch (HttpMessageConversionException exception) {
            throw new ConversationException(
                    ConversationErrorCode.AI_DIALOGUE_INVALID_RESPONSE);
        } catch (RestClientException exception) {
            throw new ConversationException(
                    ConversationErrorCode.AI_DIALOGUE_UNAVAILABLE);
        }
    }
}
