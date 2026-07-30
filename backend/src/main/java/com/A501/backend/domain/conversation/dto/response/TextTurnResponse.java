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
        return of(childTurn, assistantTurn, conversationState, null);
    }

    public static TextTurnResponse of(
            ConversationTurn childTurn,
            ConversationTurn assistantTurn,
            ConversationStatus conversationState,
            String closingMessage) {
        TextTurnItemResponse assistantResponse = assistantTurn == null
                ? closingResponse(childTurn, closingMessage)
                : TextTurnItemResponse.from(assistantTurn);
        return new TextTurnResponse(
                TextTurnItemResponse.from(childTurn),
                assistantResponse,
                conversationState);
    }

    private static TextTurnItemResponse closingResponse(
            ConversationTurn childTurn,
            String closingMessage) {
        if (closingMessage == null || closingMessage.isBlank()) {
            return null;
        }
        return new TextTurnItemResponse(
                null,
                childTurn.getTurnSeq() + 1,
                null,
                childTurn.getTopicType(),
                closingMessage);
    }
}
