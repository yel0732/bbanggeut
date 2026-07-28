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
class ParentFinderTest {

    @Mock
    private ParentRepository parentRepository;

    private ParentFinder parentFinder;

    @BeforeEach
    void setUp() {
        parentFinder = new ParentFinder(parentRepository);
    }

    @Test
    void getActive_returnsParentWhenNotDeleted() {
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));

        assertThat(parentFinder.getActive(1L)).isSameAs(parent);
    }

    @Test
    void getActive_throwsWhenParentNotFound() {
        when(parentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> parentFinder.getActive(99L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.PARENT_NOT_FOUND));
    }

    // 탈퇴 계정이 만료 전 access token으로 인증 필요 API를 계속 쓰는 것을 막는 지점이라,
    // 미존재와 동일하게 PARENT_NOT_FOUND로 끝나야 한다.
    @Test
    void getActive_throwsWhenParentIsSoftDeleted() {
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        ReflectionTestUtils.setField(parent, "deletedAt", LocalDateTime.now());
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> parentFinder.getActive(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.PARENT_NOT_FOUND));
    }
}
