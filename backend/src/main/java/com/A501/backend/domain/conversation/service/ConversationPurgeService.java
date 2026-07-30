package com.A501.backend.domain.conversation.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.conversation.repository.ConversationEventFailureRepository;
import com.A501.backend.domain.conversation.repository.ConversationEventRepository;
import com.A501.backend.domain.conversation.repository.ConversationSessionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConversationPurgeService {

    private final ConversationSessionRepository sessionRepository;
    private final ConversationEventRepository eventRepository;
    private final ConversationEventFailureRepository eventFailureRepository;
    private final ConversationContentPurger contentPurger;

    @Transactional
    public void purgeByChildIds(List<Long> childIds) {
        if (childIds == null || childIds.isEmpty()) {
            return;
        }

        List<String> conversationIds = sessionRepository.findIdsByChildIds(childIds);
        if (conversationIds.isEmpty()) {
            return;
        }

        conversationIds.forEach(contentPurger::purge);
        eventFailureRepository.deleteByConversationIds(conversationIds);
        eventRepository.deleteByConversationIds(conversationIds);
        sessionRepository.deleteByConversationIds(conversationIds);
    }
}
