package com.A501.backend.domain.childdiary.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.A501.backend.domain.childdiary.repository.ChildDiaryRepository;

@ExtendWith(MockitoExtension.class)
class ChildDiaryPurgeServiceTest {

    @Mock
    private ChildDiaryRepository childDiaryRepository;

    @InjectMocks
    private ChildDiaryPurgeService childDiaryPurgeService;

    @Test
    void skipsPurgeWhenChildIdsAreEmpty() {
        childDiaryPurgeService.purgeByChildIds(List.of());

        verify(childDiaryRepository, never()).deleteByChildIds(List.of());
    }

    @Test
    void deletesDiariesForChildren() {
        List<Long> childIds = List.of(1L, 2L);

        childDiaryPurgeService.purgeByChildIds(childIds);

        verify(childDiaryRepository).deleteByChildIds(childIds);
    }
}
