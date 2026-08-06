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
    private final ConversationReportTextSanitizer textSanitizer;

    public ConversationReportAdapter(
            ConversationSessionRepository sessionRepository,
            ConversationTopicStateRepository topicStateRepository,
            ConversationTurnRepository turnRepository,
            ObjectMapper objectMapper,
            ReportAiSummaryPort reportAiSummaryPort,
            GameReportQueryPort gameReportQueryPort,
            ReportAiProperties reportAiProperties,
            ConversationReportTextSanitizer textSanitizer
    ) {
        this.sessionRepository = sessionRepository;
        this.topicStateRepository = topicStateRepository;
        this.turnRepository = turnRepository;
        this.objectMapper = objectMapper;
        this.reportAiSummaryPort = reportAiSummaryPort;
        this.gameReportQueryPort = gameReportQueryPort;
        this.reportAiProperties = reportAiProperties;
        this.textSanitizer = textSanitizer;
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
        boolean hasSafetySignal = turns.stream()
                .map(ConversationTurn::getContent)
                .anyMatch(textSanitizer::containsSafetySignal);
        List<ReportAiConversationTurn> conversations = turns.stream()
                .limit(60)
                .map(turn -> new SanitizedTurn(
                        turn,
                        textSanitizer.sanitizeForAi(turn.getContent())))
                .filter(turn -> hasText(turn.content()))
                .map(turn -> new ReportAiConversationTurn(
                        turn.turn().getSpeaker().name(),
                        limit(turn.content(), 500)))
                .toList();
        List<String> childUtterances = turns.stream()
                .filter(turn -> turn.getSpeaker() == Speaker.CHILD)
                .map(ConversationTurn::getContent)
                .filter(this::hasText)
                .map(String::trim)
                .filter(textSanitizer::canUseAsQuote)
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
                protectedGuide(ReportConversationGuide.empty(), hasSafetySignal),
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
                    protectedGuide(ai.conversationGuide(), hasSafetySignal),
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
            String people = String.join(", ", slots.people());
            parts.add(people + withBatchim(people, "과", "와") + " 함께했다");
        }
        if (hasText(slots.place())) {
            String place = slots.place().trim();
            parts.add("장소는 " + place + withBatchim(place, "이었다", "였다"));
        }
        if (hasText(slots.emotion())) {
            // 감정 슬롯에는 "즐거웠어"처럼 이미 서술형이 들어온다. 여기에 "했다"를
            // 덧붙이면 "기분은 즐거웠어했다"가 된다.
            String emotion = "기분은 " + toPlainStatement(slots.emotion().trim());
            if (hasText(slots.emotionReason())) {
                emotion += ". " + slots.emotionReason().trim();
            }
            parts.add(emotion);
        }
        if (hasText(slots.memorableMoment())) {
            String moment = slots.memorableMoment().trim();
            parts.add("기억에 남는 순간은 "
                    + moment + withBatchim(moment, "이었다", "였다"));
        }
        return parts.isEmpty() ? "" : String.join(". ", parts) + ".";
    }

    private static final char HANGUL_BASE = 0xAC00;
    private static final char HANGUL_LAST = 0xD7A3;
    private static final int JONGSEONG_COUNT = 28;

    /** 받침이 있으면 {@code whenBatchim}, 없으면 {@code whenNone}을 고른다. */
    private String withBatchim(String value, String whenBatchim, String whenNone) {
        char last = value.charAt(value.length() - 1);
        if (last < HANGUL_BASE || last > HANGUL_LAST) {
            return whenNone;
        }
        return (last - HANGUL_BASE) % JONGSEONG_COUNT == 0 ? whenNone : whenBatchim;
    }

    /**
     * 구어체 종결어미를 평서형으로 바꾼다. 슬롯에는 아이 표현이 그대로 담기므로
     * 문장으로 이어 붙이기 전에 어미를 맞춘다. 바꿀 어미가 없으면 원문을 둔다.
     */
    private String toPlainStatement(String value) {
        for (String ending : new String[] {"어요", "어"}) {
            if (!value.endsWith(ending)) {
                continue;
            }
            String stem = value.substring(0, value.length() - ending.length());
            if (stem.isEmpty()) {
                return value;
            }
            char last = stem.charAt(stem.length() - 1);
            // 과거 시제 받침 ㅆ(종성 20)이면 "했어 → 했다"처럼 평서형으로 바꾼다.
            if (last >= HANGUL_BASE && last <= HANGUL_LAST
                    && (last - HANGUL_BASE) % JONGSEONG_COUNT == 20) {
                return stem + "다";
            }
            return value;
        }
        return value;
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

    /**
     * 아이가 힘든 마음을 드러낸 대화에는 부모가 먼저 살펴볼 수 있도록 안내를 얹는다.
     * 안전 신호가 없으면 AI가 만든 안내를 그대로 둔다.
     */
    private ReportConversationGuide protectedGuide(
            ReportConversationGuide original,
            boolean hasSafetySignal
    ) {
        if (!hasSafetySignal) {
            return original;
        }
        List<String> questions = new ArrayList<>(original.questions());
        questions.add("오늘 마음이 많이 힘들었던 일이 있었는지 편안히 물어봐 주세요.");
        return new ReportConversationGuide(
                "대화 중 아이와 편안히 이야기해볼 필요가 있는 표현이 있었습니다.",
                questions);
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

    private record SanitizedTurn(
            ConversationTurn turn,
            String content
    ) {
    }
}
