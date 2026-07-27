package com.A501.backend.domain.child.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.auth.exception.PinErrorCode;
import com.A501.backend.domain.auth.service.PinService;
import com.A501.backend.domain.child.entity.Child;
import com.A501.backend.domain.child.exception.ChildErrorCode;
import com.A501.backend.domain.child.repository.ChildRepository;
import com.A501.backend.domain.child.security.ChildProperties;

@ExtendWith(MockitoExtension.class)
class ChildServiceTest {

    private static final ChildProperties CHILD_PROPERTIES = new ChildProperties(10);

    @Mock
    private ChildRepository childRepository;

    @Mock
    private PinService pinService;

    private ChildService childService;

    @BeforeEach
    void setUp() {
        childService = new ChildService(childRepository, CHILD_PROPERTIES, pinService);
    }

    @Test
    void isNameAvailable_returnsTrueWhenNoActiveChildHasName() {
        when(childRepository.existsByParentIdAndNameAndDeletedAtIsNull(1L, "chorong")).thenReturn(false);

        assertThat(childService.isNameAvailable(1L, "chorong")).isTrue();
    }

    @Test
    void isNameAvailable_returnsFalseWhenActiveChildHasName() {
        when(childRepository.existsByParentIdAndNameAndDeletedAtIsNull(1L, "chorong")).thenReturn(true);

        assertThat(childService.isNameAvailable(1L, "chorong")).isFalse();
    }

    @Test
    void listChildren_returnsChildrenOwnedByParent() {
        Child child = Child.create(1L, "chorong", 1);
        when(childRepository.findAllByParentIdAndDeletedAtIsNull(1L)).thenReturn(List.of(child));

        assertThat(childService.listChildren(1L)).containsExactly(child);
    }

    @Test
    void softDeleteChildrenOf_softDeletesAllActiveChildren() {
        Child first = Child.create(1L, "chorong", 1);
        Child second = Child.create(1L, "banjjak", 2);
        when(childRepository.findAllByParentIdAndDeletedAtIsNull(1L)).thenReturn(List.of(first, second));

        childService.softDeleteChildrenOf(1L);

        assertThat(first.isDeleted()).isTrue();
        assertThat(second.isDeleted()).isTrue();
    }

    @Test
    void createChild_savesChildWhenPinSetAndUnderLimitAndNameAvailable() {
        when(pinService.hasPin(1L)).thenReturn(true);
        when(childRepository.countByParentIdAndDeletedAtIsNull(1L)).thenReturn(0L);
        when(childRepository.existsByParentIdAndNameAndDeletedAtIsNull(1L, "chorong")).thenReturn(false);
        when(childRepository.save(any(Child.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Child child = childService.createChild(1L, "chorong", 3);

        assertThat(child.getName()).isEqualTo("chorong");
        assertThat(child.getProfileImageNum()).isEqualTo(3);
    }

    @Test
    void createChild_throwsWhenPinNotSet() {
        when(pinService.hasPin(1L)).thenReturn(false);

        assertThatThrownBy(() -> childService.createChild(1L, "chorong", 1))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PinErrorCode.PIN_NOT_SET));
        verify(childRepository, never()).save(any());
    }

    @Test
    void createChild_throwsWhenChildLimitReached() {
        when(pinService.hasPin(1L)).thenReturn(true);
        when(childRepository.countByParentIdAndDeletedAtIsNull(1L)).thenReturn(10L);

        assertThatThrownBy(() -> childService.createChild(1L, "chorong", 1))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ChildErrorCode.CHILD_LIMIT_EXCEEDED));
        verify(childRepository, never()).save(any());
    }

    @Test
    void createChild_throwsWhenNameAlreadyExists() {
        when(pinService.hasPin(1L)).thenReturn(true);
        when(childRepository.countByParentIdAndDeletedAtIsNull(1L)).thenReturn(0L);
        when(childRepository.existsByParentIdAndNameAndDeletedAtIsNull(1L, "chorong")).thenReturn(true);

        assertThatThrownBy(() -> childService.createChild(1L, "chorong", 1))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ChildErrorCode.CHILD_NAME_ALREADY_EXISTS));
        verify(childRepository, never()).save(any());
    }

    @Test
    void updateChild_renamesAndChangesImageWhenOwnedAndNameAvailable() {
        Child child = Child.create(1L, "chorong", 1);
        when(childRepository.findByChildIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(child));
        when(childRepository.existsByParentIdAndNameAndDeletedAtIsNull(1L, "banjjak")).thenReturn(false);

        Child updated = childService.updateChild(1L, 10L, "banjjak", 5);

        assertThat(updated.getName()).isEqualTo("banjjak");
        assertThat(updated.getProfileImageNum()).isEqualTo(5);
    }

    @Test
    void updateChild_allowsKeepingSameNameWithoutDuplicateCheck() {
        Child child = Child.create(1L, "chorong", 1);
        when(childRepository.findByChildIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(child));

        Child updated = childService.updateChild(1L, 10L, "chorong", 5);

        assertThat(updated.getProfileImageNum()).isEqualTo(5);
        verify(childRepository, never()).existsByParentIdAndNameAndDeletedAtIsNull(any(), any());
    }

    @Test
    void updateChild_throwsWhenChildNotFound() {
        when(childRepository.findByChildIdAndDeletedAtIsNull(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> childService.updateChild(1L, 10L, "banjjak", 5))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ChildErrorCode.CHILD_NOT_FOUND));
    }

    @Test
    void updateChild_throwsNotFoundWhenChildBelongsToAnotherParent() {
        Child child = Child.create(2L, "chorong", 1);
        when(childRepository.findByChildIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(child));

        assertThatThrownBy(() -> childService.updateChild(1L, 10L, "banjjak", 5))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ChildErrorCode.CHILD_NOT_FOUND));
    }

    @Test
    void updateChild_throwsWhenNewNameAlreadyUsedByAnotherChild() {
        Child child = Child.create(1L, "chorong", 1);
        when(childRepository.findByChildIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(child));
        when(childRepository.existsByParentIdAndNameAndDeletedAtIsNull(1L, "banjjak")).thenReturn(true);

        assertThatThrownBy(() -> childService.updateChild(1L, 10L, "banjjak", 5))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ChildErrorCode.CHILD_NAME_ALREADY_EXISTS));
        assertThat(child.getName()).isEqualTo("chorong");
    }

    @Test
    void deleteChild_softDeletesWhenOwnedAndPinCorrect() {
        Child child = Child.create(1L, "chorong", 1);
        when(childRepository.findByChildIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(child));

        childService.deleteChild(1L, 10L, "1234");

        verify(pinService).verifyPin(1L, "1234");
        assertThat(child.isDeleted()).isTrue();
    }

    @Test
    void deleteChild_throwsNotFoundBeforeCheckingPinWhenChildMissing() {
        when(childRepository.findByChildIdAndDeletedAtIsNull(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> childService.deleteChild(1L, 10L, "1234"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ChildErrorCode.CHILD_NOT_FOUND));
        // 존재하지 않는/소유하지 않은 자녀 요청은 PIN 실패 카운터를 소모하면 안 된다.
        verify(pinService, never()).verifyPin(any(), any());
    }

    @Test
    void deleteChild_throwsNotFoundWhenChildBelongsToAnotherParent() {
        Child child = Child.create(2L, "chorong", 1);
        when(childRepository.findByChildIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(child));

        assertThatThrownBy(() -> childService.deleteChild(1L, 10L, "1234"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ChildErrorCode.CHILD_NOT_FOUND));
        verify(pinService, never()).verifyPin(any(), any());
    }

    @Test
    void deleteChild_propagatesPinMismatchAndLeavesChildAlive() {
        Child child = Child.create(1L, "chorong", 1);
        when(childRepository.findByChildIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(child));
        doThrow(new BusinessException(PinErrorCode.PIN_MISMATCH)).when(pinService).verifyPin(1L, "0000");

        assertThatThrownBy(() -> childService.deleteChild(1L, 10L, "0000"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PinErrorCode.PIN_MISMATCH));
        assertThat(child.isDeleted()).isFalse();
    }
}
