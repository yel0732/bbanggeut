package com.A501.backend.domain.child.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.auth.entity.Parent;
import com.A501.backend.domain.auth.exception.AuthErrorCode;
import com.A501.backend.domain.auth.repository.ParentRepository;
import com.A501.backend.domain.child.entity.Child;
import com.A501.backend.domain.child.repository.ChildRepository;

/**
 * 탈퇴 계정의 만료 전 access token으로 자녀 API를 호출하면 전부 401이어야 한다.
 *
 * <p>게이트가 없던 시절에는 경로마다 응답이 달랐다 — 조회는 200 빈 배열, 수정·삭제는 자녀가
 * 소프트 삭제된 탓에 404. 프론트가 401을 기준으로 로그아웃을 처리하므로 그 경로들이 빠졌다.
 */
@SpringBootTest
@Transactional
class ChildServiceWithdrawnGateTest {

    private static final String PRESET_KEY = "profile-presets/child-avatar";

    @Autowired
    private ChildService childService;

    @Autowired
    private ParentRepository parentRepository;

    @Autowired
    private ChildRepository childRepository;

    private Long withdrawnParentId;
    private Long childId;

    @BeforeEach
    void setUp() {
        Parent parent = Parent.createLocal("gate@example.com", "hash", LocalDate.of(1990, 1, 1));
        parent.changeProfileImage("profile-presets/parent-avatar");
        withdrawnParentId = parentRepository.saveAndFlush(parent).getParentId();
        childId = childRepository.saveAndFlush(
                Child.create(withdrawnParentId, "chorong", PRESET_KEY)).getChildId();

        parent.withdraw(LocalDateTime.now());
        parentRepository.saveAndFlush(parent);
    }

    @Test
    void listChildren_rejectsWithdrawnAccount() {
        assertParentNotFound(() -> childService.listChildren(withdrawnParentId));
    }

    @Test
    void isNameAvailable_rejectsWithdrawnAccount() {
        assertParentNotFound(() -> childService.isNameAvailable(withdrawnParentId, "anything"));
    }

    @Test
    void createChild_rejectsWithdrawnAccount() {
        assertParentNotFound(() -> childService.createChild(withdrawnParentId, "new", PRESET_KEY));
    }

    @Test
    void updateChild_rejectsWithdrawnAccount() {
        assertParentNotFound(
                () -> childService.updateChild(withdrawnParentId, childId, "renamed", PRESET_KEY));
    }

    @Test
    void uploadChildImage_rejectsWithdrawnAccount() {
        MockMultipartFile file =
                new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[] {1, 2, 3});

        assertParentNotFound(
                () -> childService.uploadChildImage(withdrawnParentId, childId, file));
    }

    @Test
    void deleteChild_rejectsWithdrawnAccount() {
        assertParentNotFound(() -> childService.deleteChild(withdrawnParentId, childId, "1234"));
    }

    // 활성 계정은 그대로 동작해야 한다 — 게이트가 정상 경로를 막지 않는지 확인한다.
    @Test
    void activeAccountStillWorks() {
        Parent active = Parent.createLocal("active@example.com", "hash", LocalDate.of(1990, 1, 1));
        active.changeProfileImage("profile-presets/parent-avatar");
        Long activeId = parentRepository.saveAndFlush(active).getParentId();
        childRepository.saveAndFlush(Child.create(activeId, "alive", PRESET_KEY));

        assertThat(childService.listChildren(activeId)).hasSize(1);
        assertThat(childService.isNameAvailable(activeId, "unused")).isTrue();
    }

    private void assertParentNotFound(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.PARENT_NOT_FOUND));
    }
}
