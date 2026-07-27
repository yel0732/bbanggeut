package com.A501.backend.domain.child.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.child.repository.ChildRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChildService {

    private final ChildRepository childRepository;

    public boolean isNameAvailable(Long parentId, String name) {
        return !childRepository.existsByParentIdAndNameAndDeletedAtIsNull(parentId, name);
    }
}
