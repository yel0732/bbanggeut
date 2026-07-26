package com.A501.backend.domain.conversation.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.A501.backend.common.util.ApiDateTimeMapper;
import com.A501.backend.domain.conversation.entity.ConversationTurn;
import com.A501.backend.domain.conversation.entity.ConversationTopic;
import com.A501.backend.domain.conversation.entity.Speaker;
import com.A501.backend.domain.conversation.entity.TurnStatus;

public record ConversationTurnResponse(
        String turnId,
        int turnSeq,
        Speaker speaker,
        ConversationTopic topicType,
        String content,
        TurnStatus status,
        String responseId,
        BigDecimal sttConfidence,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt) {

    public static ConversationTurnResponse from(ConversationTurn turn) {
        return new ConversationTurnResponse(
                turn.getTurnId(),
                turn.getTurnSeq(),
                turn.getSpeaker(),
                turn.getTopicType(),
                turn.getContent(),
                turn.getStatus(),
                turn.getResponseId(),
                turn.getSttConfidence(),
                ApiDateTimeMapper.utcToKst(turn.getStartedAt()),
                ApiDateTimeMapper.utcToKst(turn.getCompletedAt()));
    }
}
