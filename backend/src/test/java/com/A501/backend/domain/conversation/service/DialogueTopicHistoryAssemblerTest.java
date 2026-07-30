package com.A501.backend.domain.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.A501.backend.domain.conversation.dto.internal.DialogueTopicHistoryPayload;
import com.A501.backend.domain.conversation.entity.ConversationTopic;
import com.A501.backend.domain.conversation.entity.ConversationTurn;

class DialogueTopicHistoryAssemblerTest {

    @Test
    void 현재_주제의_완료된_질문답변과_마지막_질문을_분리한다() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 30, 1, 0);
        ConversationTurn firstQuestion = aiTurn(
                "turn_ai_1",
                1,
                "과일을 잘랐구나! 그때 기분이 어땠어?",
                "그때 기분이 어땠어?",
                now);
        ConversationTurn firstAnswer = ConversationTurn.createChildCompleted(
                "turn_child_1",
                "conv_history",
                2,
                "정말 신났어",
                null,
                now,
                now,
                ConversationTopic.GAME);
        ConversationTurn currentQuestion = aiTurn(
                "turn_ai_2",
                3,
                "정말 신났구나! 어떤 점 때문에 신났어?",
                "어떤 점 때문에 신났어?",
                now);

        List<ConversationTurn> turns =
                List.of(firstQuestion, firstAnswer, currentQuestion);

        assertThat(DialogueTopicHistoryAssembler.completedExchanges(turns))
                .containsExactly(new DialogueTopicHistoryPayload(
                        firstQuestion.getContent(),
                        firstQuestion.getQuestionText(),
                        firstAnswer.getContent()));
        assertThat(DialogueTopicHistoryAssembler.currentAssistantTurn(turns))
                .isSameAs(currentQuestion);
    }

    @Test
    void 지원하는_질문_목표만_FastAPI_계약으로_전달한다() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 30, 1, 0);
        ConversationTurn supported = aiTurn(
                "turn_supported",
                1,
                "정말 신났구나! 어떤 점 때문에 신났어?",
                "어떤 점 때문에 신났어?",
                "emotionReason",
                now);
        ConversationTurn legacy = aiTurn(
                "turn_legacy",
                2,
                "산책했구나! 어디에서 산책했어?",
                "어디에서 산책했어?",
                "place",
                now);
        ConversationTurn missing = aiTurn(
                "turn_missing",
                3,
                "그랬구나! 그때 기분이 어땠어?",
                "그때 기분이 어땠어?",
                null,
                now);

        assertThat(DialogueTopicHistoryAssembler.resolveSupportedQuestionTarget(supported))
                .isEqualTo("emotionReason");
        assertThat(DialogueTopicHistoryAssembler.resolveSupportedQuestionTarget(legacy))
                .isNull();
        assertThat(DialogueTopicHistoryAssembler.resolveSupportedQuestionTarget(missing))
                .isNull();
    }

    private ConversationTurn aiTurn(
            String turnId,
            int turnSeq,
            String message,
            String question,
            LocalDateTime now) {
        return aiTurn(turnId, turnSeq, message, question, null, now);
    }

    private ConversationTurn aiTurn(
            String turnId,
            int turnSeq,
            String message,
            String question,
            String questionTarget,
            LocalDateTime now) {
        ConversationTurn turn = ConversationTurn.createAiGenerating(
                turnId,
                "conv_history",
                turnSeq,
                message,
                question,
                questionTarget,
                "resp_" + turnSeq,
                now,
                ConversationTopic.GAME);
        turn.complete(now);
        return turn;
    }
}
