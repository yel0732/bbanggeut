package com.A501.backend.domain.conversation.dto.response;

import com.A501.backend.domain.conversation.entity.ConversationStatus;
import com.A501.backend.domain.conversation.entity.ConversationTurn;

public record TextTurnResponse(
        TextTurnItemResponse childTurn,
        TextTurnItemResponse assistantTurn,
        ConversationStatus conversationState) {

    public static TextTurnResponse of(
            ConversationTurn childTurn,
            ConversationTurn assistantTurn,
            ConversationStatus conversationState) {
        return new TextTurnResponse(
                TextTurnItemResponse.from(childTurn),
                assistantTurn == null ? null : TextTurnItemResponse.from(assistantTurn),
                conversationState);
    }
}
