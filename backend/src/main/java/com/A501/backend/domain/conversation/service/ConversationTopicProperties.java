package com.A501.backend.domain.conversation.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "conversation.topic")
public record ConversationTopicProperties(
        int gameMaxQuestionCount,
        int dailyMaxQuestionCount) {

    public ConversationTopicProperties {
        if (gameMaxQuestionCount < 1 || dailyMaxQuestionCount < 1) {
            throw new IllegalArgumentException("주제별 최대 질문 수는 1 이상이어야 합니다.");
        }
    }
}
