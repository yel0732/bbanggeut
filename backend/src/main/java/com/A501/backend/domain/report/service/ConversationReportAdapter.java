package com.A501.backend.domain.report.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.A501.backend.domain.conversation.dto.internal.DialogueSlotsPayload;
import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.ConversationStatus;
import com.A501.backend.domain.conversation.entity.ConversationTopic;
import com.A501.backend.domain.conversation.entity.ConversationTopicState;
import com.A501.backend.domain.conversation.entity.ConversationTurn;
import com.A501.backend.domain.conversation.entity.Speaker;
import com.A501.backend.domain.conversation.entity.TurnStatus;
import com.A501.backend.domain.conversation.repository.ConversationSessionRepository;
import com.A501.backend.domain.conversation.repository.ConversationTopicStateRepository;
import com.A501.backend.domain.conversation.repository.ConversationTurnRepository;
import com.A501.backend.domain.report.model.ReportAiConversationTurn;
import com.A501.backend.domain.report.model.ReportConversationGuide;
import com.A501.backend.domain.report.model.ReportConversationSummarySource;
import com.A501.backend.domain.report.model.ReportAiGameInput;
import com.A501.backend.domain.report.model.ReportAiProperties;
import com.A501.backend.domain.report.model.ReportAiSummaryRequest;
import com.A501.backend.domain.report.model.ReportAiSummaryResponse;
import com.A501.backend.domain.report.model.ReportGameSource;
import com.A501.backend.domain.report.port.ConversationReportQueryPort;
import com.A501.backend.domain.report.port.ConversationSummaryPort;
import com.A501.backend.domain.report.port.GameReportQueryPort;
import com.A501.backend.domain.report.port.ReportAiSummaryPort;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class ConversationReportAdapter
        implements ConversationReportQueryPort, ConversationSummaryPort {

    private static final String MODEL_VERSION = "validated-dialogue-slots-v1";
    private static final String PROMPT_VERSION = "deterministic-report-v1";

    private final ConversationSessionRepository sessionRepository;
    private final ConversationTopicStateRepository topicStateRepository;
    private final ConversationTurnRepository turnRepository;
    private final ObjectMapper objectMapper;
    private final ReportAiSummaryPort reportAiSummaryPort;
    private final GameReportQueryPort gameReportQueryPort;
    private final ReportAiProperties reportAiProperties;

    public ConversationReportAdapter(
            ConversationSessionRepository sessionRepository,
            ConversationTopicStateRepository topicStateRepository,
            ConversationTurnRepository turnRepository,
            ObjectMapper objectMapper,
            ReportAiSummaryPort reportAiSummaryPort,
            GameReportQueryPort gameReportQueryPort,
            ReportAiProperties reportAiProperties
    ) {
        this.sessionRepository = sessionRepository;
        this.topicStateRepository = topicStateRepository;
        this.turnRepository = turnRepository;
        this.objectMapper = objectMapper;
        this.reportAiSummaryPort = reportAiSummaryPort;
        this.gameReportQueryPort = gameReportQueryPort;
        this.reportAiProperties = reportAiProperties;
    }

    @Override
    public boolean existsReportableConversation(
            Long childId,
            LocalDateTime startedAt,
            LocalDateTime endedAt
    ) {
        return !findSources(childId, startedAt, endedAt).isEmpty();
    }

    @Override
    public Optional<ReportConversationSummarySource> generateSummary(
            Long childId,
            LocalDateTime startedAt,
            LocalDateTime endedAt
    ) {
        List<ConversationSource> sources = findSources(
                childId, startedAt, endedAt);
        List<ReportGameSource> games = gameReportQueryPort.findCompletedGames(
                childId, startedAt, endedAt);
        List<ConversationTurn> turns = findCompletedTurns(sources);
        List<ReportAiConversationTurn> conversations = turns.stream()
                .limit(60)
                .map(turn -> new ReportAiConversationTurn(
                        turn.getSpeaker().name(),
                        limit(turn.getContent(), 500)))
                .toList();
        List<String> childUtterances = turns.stream()
                .filter(turn -> turn.getSpeaker() == Speaker.CHILD)
                .map(ConversationTurn::getContent)
                .filter(this::hasText)
                .map(String::trim)
                .filter(quote -> quote.length() <= 200)
                .distinct()
                .toList();
        List<String> childQuotes = childUtterances.stream()
                .limit(3)
                .toList();

        LinkedHashSet<String> events = new LinkedHashSet<>();
        LinkedHashSet<String> emotions = new LinkedHashSet<>();
        List<String> contents = new ArrayList<>();
        for (ConversationSource source : sources) {
            DialogueSlotsPayload slots = source.slots();
            addIfPresent(events, slots.event());
            addIfPresent(events, slots.memorableMoment());
            addIfPresent(emotions, slots.emotion());
            String sentence = toDiarySentence(slots);
            if (!sentence.isBlank()) {
                contents.add(sentence);
            }
        }

        if (contents.isEmpty() && games.isEmpty() && conversations.isEmpty()) {
            return Optional.empty();
        }
        if (contents.isEmpty()) {
            if (!childQuotes.isEmpty()) {
                contents.add(String.join(" ", childQuotes));
            } else {
                contents.add(games.stream()
                        .map(game -> game.gameName() + " 게임에 참여했다")
                        .distinct()
                        .reduce((left, right) -> left + ". " + right)
                        .orElseThrow() + ".");
            }
        }
        String firstEvent = events.stream().findFirst()
                .orElse(games.isEmpty() ? "오늘의 이야기" : "오늘의 게임 활동");
        String title = firstEvent.length() <= 190
                ? firstEvent + " 이야기"
                : firstEvent.substring(0, 190) + " 이야기";
        ReportConversationSummarySource fallback =
                new ReportConversationSummarySource(
                sources.stream().map(ConversationSource::conversationId).toList(),
                title,
                String.join(" ", contents),
                List.copyOf(events),
                List.copyOf(emotions),
                List.of(),
                List.of(),
                childQuotes,
                ReportConversationGuide.empty(),
                MODEL_VERSION,
                PROMPT_VERSION);
        if (!reportAiProperties.enabled()) {
            return Optional.of(fallback);
        }
        try {
            ReportAiSummaryResponse ai = reportAiSummaryPort.generate(
                    new ReportAiSummaryRequest(
                            endedAt.toLocalDate(),
                            fallback.mainEvents(),
                            fallback.emotions(),
                            fallback.interests(),
                            conversations,
                            games.stream()
                                    .map(game -> new ReportAiGameInput(
                                            game.gameName(),
                                            game.playDurationSeconds(),
                                            game.score()))
                                    .toList()));
            return Optional.of(new ReportConversationSummarySource(
                    fallback.conversationIds(),
                    ai.title(),
                    ai.dailyStory(),
                    fallback.mainEvents(),
                    fallback.emotions(),
                    ai.interests(),
                    ai.emotionDetails(),
                    verifiedQuotes(ai.notableQuotes(), childUtterances),
                    ai.conversationGuide(),
                    ai.modelVersion(),
                    ai.promptVersion()));
        } catch (ReportAiUnavailableException exception) {
            return Optional.of(fallback);
        }
    }

    private List<ConversationSource> findSources(
            Long childId,
            LocalDateTime startedAt,
            LocalDateTime endedAt
    ) {
        validatePeriod(childId, startedAt, endedAt);
        List<ConversationSession> sessions = sessionRepository
                .findAllByChildIdAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThanOrderByCompletedAtAsc(
                        childId,
                        ConversationStatus.COMPLETED,
                        startedAt,
                        endedAt);
        if (sessions.isEmpty()) {
            return List.of();
        }

        List<String> conversationIds = sessions.stream()
                .map(ConversationSession::getConversationId)
                .toList();
        return topicStateRepository
                .findAllByConversationIdInAndTopicTypeOrderByCompletedAtAsc(
                        conversationIds,
                        ConversationTopic.DAILY)
                .stream()
                .map(this::toSource)
                .toList();
    }

    private ConversationSource toSource(ConversationTopicState state) {
        try {
            DialogueSlotsPayload slots = objectMapper.readValue(
                    state.getSlotsJson(),
                    DialogueSlotsPayload.class);
            return new ConversationSource(
                    state.getConversationId(),
                    slots);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Failed to deserialize conversation diary slots.", exception);
        }
    }

    private List<ConversationTurn> findCompletedTurns(List<ConversationSource> sources) {
        List<String> conversationIds = sources.stream()
                .map(ConversationSource::conversationId)
                .toList();
        if (conversationIds.isEmpty()) {
            return List.of();
        }
        return turnRepository
                .findAllByConversationIdInAndTopicTypeAndStatusOrderByConversationIdAscTurnSeqAsc(
                        conversationIds,
                        ConversationTopic.DAILY,
                        TurnStatus.COMPLETED)
                .stream()
                .filter(turn -> hasText(turn.getContent()))
                .toList();
    }

    private List<String> verifiedQuotes(
            List<String> generatedQuotes,
            List<String> childQuotes) {
        if (generatedQuotes == null) {
            return List.of();
        }
        return generatedQuotes.stream()
                .map(String::trim)
                .filter(childQuotes::contains)
                .distinct()
                .limit(3)
                .toList();
    }

    private String limit(String value, int maxLength) {
        String normalized = value.trim();
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength);
    }

    private String toDiarySentence(DialogueSlotsPayload slots) {
        List<String> parts = new ArrayList<>();
        if (hasText(slots.event())) {
            parts.add(slots.event().trim());
        }
        if (hasText(slots.people())) {
            parts.add(String.join(", ", slots.people()) + "와 함께했다");
        }
        if (hasText(slots.place())) {
            parts.add("장소는 " + slots.place().trim() + "였다");
        }
        if (hasText(slots.emotion())) {
            String emotion = "기분은 " + slots.emotion().trim() + "했다";
            if (hasText(slots.emotionReason())) {
                emotion += ". " + slots.emotionReason().trim();
            }
            parts.add(emotion);
        }
        if (hasText(slots.memorableMoment())) {
            parts.add("기억에 남는 순간은 "
                    + slots.memorableMoment().trim() + "였다");
        }
        return parts.isEmpty() ? "" : String.join(". ", parts) + ".";
    }

    private void addIfPresent(LinkedHashSet<String> target, String value) {
        if (hasText(value)) {
            target.add(value.trim());
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean hasText(List<String> values) {
        return values != null && values.stream().anyMatch(this::hasText);
    }

    private void validatePeriod(
            Long childId,
            LocalDateTime startedAt,
            LocalDateTime endedAt
    ) {
        if (childId == null || childId < 1) {
            throw new IllegalArgumentException("Child ID must be positive.");
        }
        if (startedAt == null || endedAt == null
                || !startedAt.isBefore(endedAt)) {
            throw new IllegalArgumentException(
                    "Report period start must be before its end.");
        }
    }

    private record ConversationSource(
            String conversationId,
            DialogueSlotsPayload slots
    ) {
    }
}
