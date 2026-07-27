package com.A501.backend.domain.childdiary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.ConversationTopic;
import com.A501.backend.domain.conversation.entity.ConversationTopicState;
import com.A501.backend.domain.conversation.entity.TerminationReason;
import com.A501.backend.domain.conversation.repository.ConversationSessionRepository;
import com.A501.backend.domain.conversation.repository.ConversationTopicStateRepository;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ChildDiarySourceServiceTest {
    @Mock ConversationSessionRepository sessionRepository;
    @Mock ConversationTopicStateRepository topicRepository;
    ChildDiarySourceService service;

    @BeforeEach
    void setUp() {
        service = new ChildDiarySourceService(sessionRepository, topicRepository,
                new ObjectMapper());
    }

    @Test
    void readsOnlyValidatedSlotsFromTheCompletedConversation() {
        ConversationSession session = completedSession();
        ConversationTopicState topic = ConversationTopicState.create("topic-1", "conversation-1",
                ConversationTopic.DAILY, 1, 6,
                "{\"event\":\"친구와 놀았다\",\"people\":[],\"emotion\":\"즐거웠다\"}", "{}");
        when(sessionRepository.findById("conversation-1")).thenReturn(Optional.of(session));
        when(topicRepository.findAllByConversationIdOrderByTopicOrderAsc("conversation-1"))
                .thenReturn(List.of(topic));

        ChildDiarySourceService.Source source = service.getSource(15L, "conversation-1");
        assertThat(source.topics()).hasSize(1);
        assertThat(source.topics().getFirst().slots().event()).isEqualTo("친구와 놀았다");
    }

    @Test
    void rejectsAConversationOwnedByAnotherChild() {
        when(sessionRepository.findById("conversation-1")).thenReturn(Optional.of(completedSession()));
        assertThatThrownBy(() -> service.getSource(99L, "conversation-1"))
                .isInstanceOf(BusinessException.class);
    }

    private ConversationSession completedSession() {
        ConversationSession session = ConversationSession.create(
                "conversation-1", 15L, ConversationTopic.DAILY, 12);
        session.start(LocalDateTime.of(2026, 7, 27, 9, 0));
        session.terminate(LocalDateTime.of(2026, 7, 27, 10, 0), TerminationReason.USER_FINISHED);
        return session;
    }
}
