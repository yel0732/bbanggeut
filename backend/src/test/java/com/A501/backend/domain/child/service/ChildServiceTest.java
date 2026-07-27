package com.A501.backend.domain.child.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.A501.backend.domain.child.repository.ChildRepository;

@ExtendWith(MockitoExtension.class)
class ChildServiceTest {

    @Mock
    private ChildRepository childRepository;

    private ChildService childService;

    @BeforeEach
    void setUp() {
        childService = new ChildService(childRepository);
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
}
