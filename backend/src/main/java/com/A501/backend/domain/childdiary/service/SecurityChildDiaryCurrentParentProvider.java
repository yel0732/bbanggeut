package com.A501.backend.domain.childdiary.service;

import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.childdiary.exception.ChildDiaryErrorCode;
import com.A501.backend.domain.childdiary.port.ChildDiaryCurrentParentProvider;

@Component
@Profile("!local")
public class SecurityChildDiaryCurrentParentProvider implements ChildDiaryCurrentParentProvider {
    @Override
    public Long getCurrentParentId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new BusinessException(ChildDiaryErrorCode.UNAUTHORIZED);
        }
        try {
            long id = Long.parseLong(authentication.getName());
            if (id < 1) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException exception) {
            throw new BusinessException(ChildDiaryErrorCode.UNAUTHORIZED);
        }
    }
}
