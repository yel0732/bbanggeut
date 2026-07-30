package com.A501.backend.domain.conversation.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.A501.backend.domain.conversation.dto.internal.DialogueTopicHistoryPayload;
import com.A501.backend.domain.conversation.entity.ConversationTurn;
import com.A501.backend.domain.conversation.entity.Speaker;

final class DialogueTopicHistoryAssembler {

    private static final Set<String> SUPPORTED_QUESTION_TARGETS =
            Set.of("event", "emotion", "emotionReason", "memorableMoment");

    private DialogueTopicHistoryAssembler() {
    }

    static List<DialogueTopicHistoryPayload> completedExchanges(
            List<ConversationTurn> orderedTurns) {
        List<DialogueTopicHistoryPayload> history = new ArrayList<>();
        ConversationTurn pendingAssistant = null;

        for (ConversationTurn turn : orderedTurns) {
            if (turn.getSpeaker() == Speaker.AI) {
                pendingAssistant = turn;
                continue;
            }
            if (turn.getSpeaker() != Speaker.CHILD || pendingAssistant == null) {
                continue;
            }
            history.add(new DialogueTopicHistoryPayload(
                    pendingAssistant.getContent(),
                    resolveQuestionText(pendingAssistant),
                    turn.getContent()));
            pendingAssistant = null;
        }
        return List.copyOf(history);
    }

    static ConversationTurn currentAssistantTurn(
            List<ConversationTurn> orderedTurns) {
        if (orderedTurns.isEmpty()) {
            return null;
        }
        ConversationTurn last = orderedTurns.get(orderedTurns.size() - 1);
        return last.getSpeaker() == Speaker.AI ? last : null;
    }

    static String resolveQuestionText(ConversationTurn turn) {
        String questionText = turn.getQuestionText();
        return questionText == null || questionText.isBlank()
                ? turn.getContent()
                : questionText;
    }

    static String resolveSupportedQuestionTarget(ConversationTurn turn) {
        String questionTarget = turn.getQuestionTarget();
        return questionTarget != null
                && SUPPORTED_QUESTION_TARGETS.contains(questionTarget)
                ? questionTarget
                : null;
    }

    static List<String> normalizeSupportedQuestionTargets(List<String> targets) {
        if (targets == null) {
            return null;
        }
        if (targets.stream().anyMatch(
                target -> target == null || !SUPPORTED_QUESTION_TARGETS.contains(target))) {
            throw new IllegalArgumentException("Unsupported dialogue question target.");
        }
        return targets.stream().distinct().sorted().toList();
    }
}
