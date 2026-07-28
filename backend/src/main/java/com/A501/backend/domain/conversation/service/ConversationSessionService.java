package com.A501.backend.domain.conversation.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import com.A501.backend.domain.conversation.dto.request.CompleteConversationRequest;
import com.A501.backend.domain.conversation.dto.internal.DialogueGameContextPayload;
import com.A501.backend.domain.conversation.dto.internal.DialogueSlotsPayload;
import com.A501.backend.domain.conversation.dto.internal.DialogueTopicContextPayload;
import com.A501.backend.domain.conversation.dto.response.CompleteConversationResponse;
import com.A501.backend.domain.conversation.dto.response.CreateConversationResponse;
import com.A501.backend.domain.conversation.dto.response.ConversationSessionResponse;
import com.A501.backend.domain.conversation.dto.response.ConversationTurnListResponse;
import com.A501.backend.domain.conversation.entity.ConversationGameSession;
import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.ConversationStatus;
import com.A501.backend.domain.conversation.entity.ConversationTopic;
import com.A501.backend.domain.conversation.entity.ConversationTopicState;
import com.A501.backend.domain.conversation.entity.ConversationTurn;
import com.A501.backend.domain.conversation.entity.Speaker;
import com.A501.backend.domain.conversation.entity.TerminationReason;
import com.A501.backend.domain.conversation.exception.ConversationErrorCode;
import com.A501.backend.domain.conversation.exception.ConversationException;
import com.A501.backend.domain.conversation.repository.ConversationGameSessionRepository;
import com.A501.backend.domain.conversation.repository.ConversationSessionRepository;
import com.A501.backend.domain.conversation.repository.ConversationTopicStateRepository;
import com.A501.backend.domain.conversation.repository.ConversationTurnRepository;
import com.A501.backend.domain.game.model.CompletedGameSessionSummary;
import com.A501.backend.domain.game.service.GameSessionService;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConversationSessionService {

    private static final Set<ConversationStatus> ACTIVE_STATUSES =
            EnumSet.of(ConversationStatus.READY, ConversationStatus.IN_PROGRESS);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private final ConversationSessionRepository conversationSessionRepository;
    private final ConversationTurnRepository conversationTurnRepository;
    private final ConversationTopicStateRepository topicStateRepository;
    private final ConversationGameSessionRepository conversationGameSessionRepository;
    private final GameSessionService gameSessionService;
    private final ChildAccessVerifier childAccessVerifier;
    private final CurrentMemberProvider currentMemberProvider;
    private final ConversationTerminationCoordinator conversationTerminationCoordinator;
    private final ConversationTopicProperties topicProperties;
    private final ObjectMapper objectMapper;
    @Qualifier("utcClock")
    private final Clock utcClock;

    @Transactional
    public CreateConversationResponse create(Long childId) {
        childAccessVerifier.verifyAccess(currentMemberProvider.getCurrentMemberId(), childId);
        validateNoActiveConversation(childId);

        List<CompletedGameSessionSummary> games = findTodayGames(childId);
        boolean hasGames = !games.isEmpty();
        ConversationTopic firstTopic = hasGames
                ? ConversationTopic.GAME
                : ConversationTopic.DAILY;
        int totalMaxQuestionCount = topicProperties.dailyMaxQuestionCount()
                + (hasGames ? topicProperties.gameMaxQuestionCount() : 0);
        String conversationId = generateConversationId();
        ConversationSession session = ConversationSession.create(
                conversationId,
                childId,
                firstTopic,
                totalMaxQuestionCount);
        conversationSessionRepository.save(session);

        List<ConversationTopicState> topics = hasGames
                ? List.of(
                        createTopic(
                                conversationId,
                                ConversationTopic.GAME,
                                1,
                                topicProperties.gameMaxQuestionCount(),
                                new DialogueTopicContextPayload(
                                        ConversationTopic.GAME,
                                        games.stream()
                                                .map(DialogueGameContextPayload::from)
                                                .toList())),
                        createTopic(
                                conversationId,
                                ConversationTopic.DAILY,
                                2,
                                topicProperties.dailyMaxQuestionCount(),
                                DialogueTopicContextPayload.empty(
                                        ConversationTopic.DAILY)))
                : List.of(
                        createTopic(
                                conversationId,
                                ConversationTopic.DAILY,
                                1,
                                topicProperties.dailyMaxQuestionCount(),
                                DialogueTopicContextPayload.empty(
                                        ConversationTopic.DAILY)));
        topicStateRepository.saveAll(topics);
        if (hasGames) {
            conversationGameSessionRepository.saveAll(
                    java.util.stream.IntStream.range(0, games.size())
                            .mapToObj(index -> ConversationGameSession.create(
                                    generateId("conv_game_"),
                                    conversationId,
                                    games.get(index).gameSessionId(),
                                    index + 1))
                            .toList());
        }

        return CreateConversationResponse.from(session, topics);
    }

    public ConversationSessionResponse getConversation(String conversationId) {
        ConversationSession session = conversationSessionRepository.findById(conversationId)
                .orElseThrow(() -> new ConversationException(ConversationErrorCode.CONVERSATION_NOT_FOUND));

        verifyConversationAccess(session);
        return ConversationSessionResponse.from(
                session,
                topicStateRepository
                        .findAllByConversationIdOrderByTopicOrderAsc(conversationId));
    }

    public ConversationTurnListResponse getConversationTurns(
            String conversationId,
            int page,
            int size,
            String speaker) {
        ConversationSession session = findConversation(conversationId);
        verifyConversationAccess(session);

        Speaker speakerFilter = parseSpeaker(speaker);
        PageRequest pageable = PageRequest.of(page, size);
        Page<ConversationTurn> turnPage = speakerFilter == null
                ? conversationTurnRepository.findByConversationIdOrderByTurnSeqAsc(
                        conversationId, pageable)
                : conversationTurnRepository.findByConversationIdAndSpeakerOrderByTurnSeqAsc(
                        conversationId, speakerFilter, pageable);

        return ConversationTurnListResponse.from(conversationId, turnPage);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CompleteConversationResponse completeConversation(
            String conversationId,
            CompleteConversationRequest request) {
        TerminationReason reason = parseTerminationReason(request == null ? null : request.reason());
        ConversationSession session = findConversation(conversationId);
        verifyConversationAccess(session);

        if (session.getStatus().isTerminal()) {
            return CompleteConversationResponse.from(session);
        }

        return CompleteConversationResponse.from(
                conversationTerminationCoordinator.terminate(conversationId, reason));
    }

    private ConversationSession findConversation(String conversationId) {
        return conversationSessionRepository.findById(conversationId)
                .orElseThrow(() -> new ConversationException(ConversationErrorCode.CONVERSATION_NOT_FOUND));
    }

    private TerminationReason parseTerminationReason(String reason) {
        try {
            return TerminationReason.from(reason);
        } catch (IllegalArgumentException exception) {
            throw new ConversationException(ConversationErrorCode.INVALID_TERMINATION_REASON);
        }
    }

    private Speaker parseSpeaker(String speaker) {
        if (speaker == null) {
            return null;
        }
        try {
            return Speaker.valueOf(speaker);
        } catch (IllegalArgumentException exception) {
            throw new ConversationException(ConversationErrorCode.INVALID_SPEAKER);
        }
    }

    private void verifyConversationAccess(ConversationSession session) {
        try {
            childAccessVerifier.verifyAccess(currentMemberProvider.getCurrentMemberId(), session.getChildId());
        } catch (ConversationException exception) {
            if (exception.getErrorCode() == ConversationErrorCode.CHILD_ACCESS_DENIED) {
                throw new ConversationException(ConversationErrorCode.CONVERSATION_ACCESS_DENIED);
            }
            if (exception.getErrorCode() == ConversationErrorCode.CHILD_NOT_FOUND) {
                throw new ConversationException(ConversationErrorCode.CONVERSATION_NOT_FOUND);
            }
            throw exception;
        }
    }

    private void validateNoActiveConversation(Long childId) {
        if (conversationSessionRepository.existsByChildIdAndStatusIn(childId, ACTIVE_STATUSES)) {
            throw new ConversationException(ConversationErrorCode.ACTIVE_CONVERSATION_EXISTS);
        }
    }

    private String generateConversationId() {
        return "conv_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String generateId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }

    private List<CompletedGameSessionSummary> findTodayGames(Long childId) {
        // game_session.completed_at에는 클라이언트가 보낸 KST wall-clock이 offset 변환 없이
        // 저장된다. 조회 경계도 같은 기준의 naive KST 자정으로 맞춘다(report 도메인의
        // ReportPeriodCalculator와 동일) — UTC로 변환해 비교하면 KST 15시 이후 완료된
        // 게임이 그날 대화에서 누락된다.
        LocalDate today = LocalDate.now(utcClock.withZone(KST));
        return gameSessionService.findCompletedSessionsForChild(
                childId,
                today.atStartOfDay(),
                today.plusDays(1).atStartOfDay());
    }

    private ConversationTopicState createTopic(
            String conversationId,
            ConversationTopic topic,
            int order,
            int maxQuestionCount,
            DialogueTopicContextPayload context) {
        return ConversationTopicState.create(
                generateId("topic_"),
                conversationId,
                topic,
                order,
                maxQuestionCount,
                serialize(DialogueSlotsPayload.empty()),
                serialize(context));
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new ConversationException(
                    ConversationErrorCode.DIALOGUE_STATE_INVALID);
        }
    }
}
