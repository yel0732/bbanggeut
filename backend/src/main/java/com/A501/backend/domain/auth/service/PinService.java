package com.A501.backend.domain.auth.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.auth.entity.Parent;
import com.A501.backend.domain.auth.exception.PinErrorCode;
import com.A501.backend.domain.auth.repository.ParentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PinService {

    private final ParentRepository parentRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void setPin(Long parentId, String rawPin) {
        Parent parent = getParent(parentId);
        if (parent.hasPin()) {
            throw new BusinessException(PinErrorCode.PIN_ALREADY_SET);
        }
        parent.setPin(passwordEncoder.encode(rawPin));
    }

    private Parent getParent(Long parentId) {
        return parentRepository.findById(parentId)
                .orElseThrow(() -> new IllegalStateException(
                        "인증된 parentId에 해당하는 Parent를 찾을 수 없습니다: " + parentId));
    }
}
