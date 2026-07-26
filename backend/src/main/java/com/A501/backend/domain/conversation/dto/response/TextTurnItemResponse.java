package com.A501.backend.domain.conversation.dto.response;

import com.A501.backend.domain.conversation.entity.ConversationTurn;
import com.A501.backend.domain.conversation.entity.ConversationTopic;

public record TextTurnItemResponse(
        String turnId,
        int turnSeq,
        String responseId,
        ConversationTopic topicType,
        String content) {

    public static TextTurnItemResponse from(ConversationTurn turn) {
        return new TextTurnItemResponse(
                turn.getTurnId(),
                turn.getTurnSeq(),
                turn.getResponseId(),
                turn.getTopicType(),
                turn.getContent());
    }
}
