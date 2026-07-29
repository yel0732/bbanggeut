package com.A501.backend.domain.childdiary.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.childdiary.repository.ChildDiaryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChildDiaryPurgeService {

    private final ChildDiaryRepository childDiaryRepository;

    @Transactional
    public void purgeByChildIds(List<Long> childIds) {
        if (childIds == null || childIds.isEmpty()) {
            return;
        }
        childDiaryRepository.deleteByChildIds(childIds);
    }
}
