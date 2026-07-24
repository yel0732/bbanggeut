package com.A501.backend.domain.conversation.dto.response;

import com.A501.backend.domain.conversation.entity.ConversationTurn;

public record TextTurnItemResponse(
        String turnId,
        int turnSeq,
        String responseId,
        String content) {

    public static TextTurnItemResponse from(ConversationTurn turn) {
        return new TextTurnItemResponse(
                turn.getTurnId(),
                turn.getTurnSeq(),
                turn.getResponseId(),
                turn.getContent());
    }
}
