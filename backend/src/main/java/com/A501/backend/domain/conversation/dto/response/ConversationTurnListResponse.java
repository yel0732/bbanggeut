package com.A501.backend.domain.conversation.dto.response;

import java.util.List;

import org.springframework.data.domain.Page;

import com.A501.backend.domain.conversation.entity.ConversationTurn;

public record ConversationTurnListResponse(
        String conversationId,
        List<ConversationTurnResponse> turns,
        int page,
        int size,
        long totalElements) {

    public static ConversationTurnListResponse from(
            String conversationId,
            Page<ConversationTurn> turnPage) {
        return new ConversationTurnListResponse(
                conversationId,
                turnPage.getContent().stream()
                        .map(ConversationTurnResponse::from)
                        .toList(),
                turnPage.getNumber(),
                turnPage.getSize(),
                turnPage.getTotalElements());
    }
}
