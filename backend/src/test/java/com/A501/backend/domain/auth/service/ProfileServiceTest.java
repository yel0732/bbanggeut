package com.A501.backend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.auth.entity.Parent;
import com.A501.backend.domain.auth.exception.AuthErrorCode;
import com.A501.backend.domain.auth.repository.ParentRepository;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private ParentRepository parentRepository;

    private ProfileService profileService;

    @BeforeEach
    void setUp() {
        // ParentFinder는 목이 아니라 실제 구현을 끼운다 — 소프트 삭제 필터가 실제로 도는지까지
        // 이 테스트가 계속 검증하도록 하기 위함이다.
        profileService = new ProfileService(new ParentFinder(parentRepository));
    }

    @Test
    void updateProfileImage_changesImageNumber() {
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));

        profileService.updateProfileImage(1L, 3);

        assertThat(parent.getProfileImageNum()).isEqualTo(3);
    }

    @Test
    void updateProfileImage_throwsWhenParentNotFound() {
        when(parentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.updateProfileImage(99L, 3))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.PARENT_NOT_FOUND));
    }

    @Test
    void updateProfileImage_throwsWhenParentIsSoftDeleted() {
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        ReflectionTestUtils.setField(parent, "deletedAt", LocalDateTime.now());
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> profileService.updateProfileImage(1L, 3))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.PARENT_NOT_FOUND));
    }
}
