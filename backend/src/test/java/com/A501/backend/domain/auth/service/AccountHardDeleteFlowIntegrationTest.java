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

import com.A501.backend.domain.auth.dto.request.SignupRequest;
import com.A501.backend.domain.auth.entity.Parent;
import com.A501.backend.domain.auth.entity.RefreshToken;
import com.A501.backend.domain.auth.repository.ParentRepository;
import com.A501.backend.domain.auth.repository.RefreshTokenRepository;
import com.A501.backend.domain.child.entity.Child;
import com.A501.backend.domain.child.repository.ChildRepository;
import com.A501.backend.domain.childdiary.entity.ChildDiary;
import com.A501.backend.domain.childdiary.entity.ChildDiaryGenerationMethod;
import com.A501.backend.domain.childdiary.repository.ChildDiaryRepository;
import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.ConversationTopic;
import com.A501.backend.domain.conversation.repository.ConversationSessionRepository;
import com.A501.backend.domain.conversation.service.RealtimeContextStore;
import com.A501.backend.domain.conversation.service.RealtimeSessionLeaseStore;

/**
 * 탈퇴부터 물리 파기, 재가입까지 실제 리포지토리로 한 번에 확인한다.
 *
 * <p>개별 단위 테스트는 각 조각만 검증한다. 파기는 되돌릴 수 없으므로 "정말 다 지워지는지"와
 * "안 지워야 할 것을 건드리지 않는지"를 전 구간으로 한 번 더 고정한다.
 */
@SpringBootTest
@Transactional
class AccountHardDeleteFlowIntegrationTest {

    private static final String PARENT_PRESET = "profile-presets/parent-avatar";
    private static final String CHILD_PRESET = "profile-presets/child-avatar";

    @Autowired
    private AuthService authService;
    @Autowired
    private AccountHardDeleteService accountHardDeleteService;
    @Autowired
    private ParentRepository parentRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private ChildRepository childRepository;
    @Autowired
    private ConversationSessionRepository conversationSessionRepository;
    @Autowired
    private ChildDiaryRepository childDiaryRepository;

    // Redis 없이도 돌아야 한다 — 검증 대상은 DB 파기다.
    @MockitoBean
    private RealtimeContextStore realtimeContextStore;
    @MockitoBean
    private RealtimeSessionLeaseStore realtimeSessionLeaseStore;

    @Test
    void purgeBatch_removesEveryTraceOfAWithdrawnAccount() {
        Long parentId = signup("flow@example.com");
        Long childId = seedChild(parentId, "chorong");
        seedToken(parentId, "hash-flow");
        seedConversation("conv-flow", childId);
        seedDiary(childId, "conv-flow");

        authService.withdraw(parentId, null);
        parentRepository.flush();

        accountHardDeleteService.purgeBatch();

        assertThat(parentRepository.findById(parentId)).isEmpty();
        assertThat(childRepository.findAllChildIdsByParentId(parentId)).isEmpty();
        assertThat(refreshTokenRepository.findByTokenHash("hash-flow")).isEmpty();
        assertThat(conversationSessionRepository.findIdsByChildIds(List.of(childId))).isEmpty();
        assertThat(childDiaryRepository.findAllByChildIdOrderByDiaryDateDescCreatedAtDesc(
                childId, org.springframework.data.domain.Pageable.unpaged())).isEmpty();
    }

    // 파기는 되돌릴 수 없다. 활성 계정이 휩쓸리면 복구 방법이 없으므로 반드시 고정한다.
    @Test
    void purgeBatch_leavesActiveAccountsUntouched() {
        Long activeParentId = signup("active@example.com");
        Long activeChildId = seedChild(activeParentId, "alive");
        seedConversation("conv-active", activeChildId);

        Long withdrawnParentId = signup("withdrawn@example.com");
        seedChild(withdrawnParentId, "gone");
        authService.withdraw(withdrawnParentId, null);
        parentRepository.flush();

        accountHardDeleteService.purgeBatch();

        assertThat(parentRepository.findById(withdrawnParentId)).isEmpty();
        assertThat(parentRepository.findById(activeParentId)).isPresent();
        assertThat(childRepository.findAllChildIdsByParentId(activeParentId))
                .containsExactly(activeChildId);
        assertThat(conversationSessionRepository.findIdsByChildIds(List.of(activeChildId)))
                .containsExactly("conv-active");
    }

    @Test
    void purgeBatch_allowsSignupWithTheSameEmailAfterwards() {
        Long parentId = signup("again@example.com");
        authService.withdraw(parentId, null);
        parentRepository.flush();

        accountHardDeleteService.purgeBatch();

        authService.signup(
                new SignupRequest("again@example.com", "Abcdef1!", LocalDate.of(1990, 1, 1)));

        Parent resignedUp = parentRepository.findByEmail("again@example.com").orElseThrow();
        assertThat(resignedUp.getParentId()).isNotEqualTo(parentId);
    }

    @Test
    void purgeBatch_isIdempotent() {
        Long parentId = signup("idem@example.com");
        seedChild(parentId, "chorong");
        authService.withdraw(parentId, null);
        parentRepository.flush();

        accountHardDeleteService.purgeBatch();
        accountHardDeleteService.purgeBatch();

        assertThat(parentRepository.findById(parentId)).isEmpty();
    }

    private Long signup(String email) {
        authService.signup(new SignupRequest(email, "Abcdef1!", LocalDate.of(1990, 1, 1)));
        return parentRepository.findByEmail(email).orElseThrow().getParentId();
    }

    private Long seedChild(Long parentId, String name) {
        return childRepository.saveAndFlush(Child.create(parentId, name, CHILD_PRESET)).getChildId();
    }

    private void seedToken(Long parentId, String tokenHash) {
        Parent parent = parentRepository.findById(parentId).orElseThrow();
        refreshTokenRepository.saveAndFlush(
                RefreshToken.issue(parent, tokenHash, LocalDateTime.now().plusDays(1), false));
    }

    private void seedConversation(String conversationId, Long childId) {
        conversationSessionRepository.saveAndFlush(
                ConversationSession.create(conversationId, childId, ConversationTopic.DAILY, 6));
    }

    private void seedDiary(Long childId, String conversationId) {
        childDiaryRepository.saveAndFlush(ChildDiary.create(
                childId, conversationId, LocalDate.now(), "제목", "[]",
                ChildDiaryGenerationMethod.AI, "model-v1", "prompt-v1"));
    }
}
