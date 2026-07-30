package com.A501.backend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.auth.entity.Parent;
import com.A501.backend.domain.auth.repository.ParentRepository;
import com.A501.backend.domain.child.entity.Child;
import com.A501.backend.domain.child.repository.ChildRepository;
import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.ConversationTopic;
import com.A501.backend.domain.conversation.repository.ConversationSessionRepository;
import com.A501.backend.domain.conversation.service.RealtimeContextStore;
import com.A501.backend.domain.conversation.service.RealtimeSessionLeaseStore;

/**
 * 보호자가 직접 삭제한 자녀의 데이터가 실제로 파기되는지, 그리고 남겨야 할 것은 남는지 확인한다.
 *
 * <p>report는 아직 연결되지 않았다(자녀 범위 purge 메서드 대기) — 이 테스트도 그 부분은 보지 않는다.
 */
@SpringBootTest
@Transactional
class DeletedChildPurgeFlowIntegrationTest {

    private static final String PARENT_PRESET = "profile-presets/parent-avatar";
    private static final String CHILD_PRESET = "profile-presets/child-avatar";

    @Autowired
    private AccountHardDeleteService accountHardDeleteService;
    @Autowired
    private ParentRepository parentRepository;
    @Autowired
    private ChildRepository childRepository;
    @Autowired
    private ConversationSessionRepository conversationSessionRepository;

    @MockitoBean
    private RealtimeContextStore realtimeContextStore;
    @MockitoBean
    private RealtimeSessionLeaseStore realtimeSessionLeaseStore;

    @Test
    void purgeDeletedChildrenBatch_removesDeletedChildButKeepsTheSibling() {
        Long parentId = saveActiveParent("keep@example.com");
        Long deletedChildId = saveChild(parentId, "gone", true);
        Long aliveChildId = saveChild(parentId, "alive", false);
        seedConversation("conv-gone", deletedChildId);
        seedConversation("conv-alive", aliveChildId);

        accountHardDeleteService.purgeDeletedChildrenBatch();

        assertThat(childRepository.findById(deletedChildId)).isEmpty();
        assertThat(childRepository.findById(aliveChildId)).isPresent();
        assertThat(conversationSessionRepository.findIdsByChildIds(List.of(deletedChildId)))
                .isEmpty();
        // 형제 자녀의 대화는 남아야 한다.
        assertThat(conversationSessionRepository.findIdsByChildIds(List.of(aliveChildId)))
                .containsExactly("conv-alive");
        // 부모 계정은 그대로다.
        assertThat(parentRepository.findById(parentId)).isPresent();
    }

    // 탈퇴한 부모의 자녀는 계정 파기 경로의 몫이다. 여기서 child row를 먼저 지우면 계정 파기가
    // childIds를 빈 목록으로 조회해 대화 데이터가 고아로 남는다.
    @Test
    void purgeDeletedChildrenBatch_leavesChildrenOfWithdrawnParentsToTheAccountPurge() {
        Long parentId = saveActiveParent("withdrawn@example.com");
        Long childId = saveChild(parentId, "gone", true);
        seedConversation("conv-withdrawn", childId);
        withdraw(parentId);

        accountHardDeleteService.purgeDeletedChildrenBatch();

        assertThat(childRepository.findById(childId)).isPresent();
        assertThat(conversationSessionRepository.findIdsByChildIds(List.of(childId)))
                .containsExactly("conv-withdrawn");
    }

    @Test
    void purgeDeletedChildrenBatch_isIdempotent() {
        Long parentId = saveActiveParent("idem@example.com");
        Long childId = saveChild(parentId, "gone", true);

        accountHardDeleteService.purgeDeletedChildrenBatch();
        accountHardDeleteService.purgeDeletedChildrenBatch();

        assertThat(childRepository.findById(childId)).isEmpty();
    }

    @Test
    void purgeDeletedChildrenBatch_doesNothingWhenNoChildIsDeleted() {
        Long parentId = saveActiveParent("clean@example.com");
        Long childId = saveChild(parentId, "alive", false);

        accountHardDeleteService.purgeDeletedChildrenBatch();

        assertThat(childRepository.findById(childId)).isPresent();
    }

    private Long saveActiveParent(String email) {
        Parent parent = Parent.createLocal(email, "hash", LocalDate.of(1990, 1, 1));
        parent.changeProfileImage(PARENT_PRESET);
        return parentRepository.saveAndFlush(parent).getParentId();
    }

    private void withdraw(Long parentId) {
        Parent parent = parentRepository.findById(parentId).orElseThrow();
        parent.withdraw(LocalDateTime.now());
        parentRepository.saveAndFlush(parent);
    }

    private Long saveChild(Long parentId, String name, boolean deleted) {
        Child child = Child.create(parentId, name, CHILD_PRESET);
        if (deleted) {
            child.softDelete(LocalDateTime.now().minusMinutes(1));
        }
        return childRepository.saveAndFlush(child).getChildId();
    }

    private void seedConversation(String conversationId, Long childId) {
        conversationSessionRepository.saveAndFlush(
                ConversationSession.create(conversationId, childId, ConversationTopic.DAILY, 6));
    }
}
