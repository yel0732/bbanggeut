package com.A501.backend.domain.childdiary.service;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.childdiary.exception.ChildDiaryErrorCode;
import com.A501.backend.domain.childdiary.model.ChildDiaryAiRequest;
import com.A501.backend.domain.conversation.dto.internal.DialogueSlotsPayload;
import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.ConversationStatus;
import com.A501.backend.domain.conversation.entity.ConversationTopicState;
import com.A501.backend.domain.conversation.repository.ConversationSessionRepository;
import com.A501.backend.domain.conversation.repository.ConversationTopicStateRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChildDiarySourceService {
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    private final ConversationSessionRepository sessionRepository;
    private final ConversationTopicStateRepository topicRepository;
    private final ObjectMapper objectMapper;

    public Source getSource(Long childId, String conversationId) {
        ConversationSession session = sessionRepository.findById(conversationId)
                .filter(value -> value.getChildId().equals(childId))
                .orElseThrow(() -> new BusinessException(ChildDiaryErrorCode.CONVERSATION_NOT_FOUND));
        if (session.getStatus() != ConversationStatus.COMPLETED || session.getCompletedAt() == null) {
            throw new BusinessException(ChildDiaryErrorCode.CONVERSATION_NOT_COMPLETED);
        }
        List<ChildDiaryAiRequest.Topic> topics = new ArrayList<>();
        for (ConversationTopicState state : topicRepository
                .findAllByConversationIdOrderByTopicOrderAsc(conversationId)) {
            DialogueSlotsPayload slots = parseSlots(state.getSlotsJson());
            if (hasContent(slots)) {
                topics.add(new ChildDiaryAiRequest.Topic(state.getTopicType().name(),
                        new ChildDiaryAiRequest.Slots(slots.event(), slots.people(), slots.place(),
                                slots.emotion(), slots.emotionReason(), slots.memorableMoment())));
            }
        }
        if (topics.isEmpty()) throw new BusinessException(ChildDiaryErrorCode.SOURCE_EMPTY);
        return new Source(session.getCompletedAt().atZone(ZoneId.of("UTC"))
                .withZoneSameInstant(SERVICE_ZONE).toLocalDate(), List.copyOf(topics));
    }

    private DialogueSlotsPayload parseSlots(String json) {
        try {
            return objectMapper.readValue(json, DialogueSlotsPayload.class);
        } catch (JacksonException exception) {
            throw new BusinessException(ChildDiaryErrorCode.SOURCE_EMPTY);
        }
    }

    private boolean hasContent(DialogueSlotsPayload slots) {
        return hasText(slots.event()) || !slots.people().isEmpty() || hasText(slots.place())
                || hasText(slots.emotion()) || hasText(slots.emotionReason())
                || hasText(slots.memorableMoment());
    }
    private boolean hasText(String value) { return value != null && !value.isBlank(); }

    public record Source(java.time.LocalDate diaryDate, List<ChildDiaryAiRequest.Topic> topics) {}
}
