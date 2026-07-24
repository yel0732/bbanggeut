package com.A501.backend.domain.report.service;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.report.exception.ReportApiErrorCode;
import com.A501.backend.domain.report.port.ReportCurrentParentProvider;

@Component
public class SecurityReportCurrentParentProvider
        implements ReportCurrentParentProvider {

    @Override
    public Long getCurrentParentId() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new BusinessException(ReportApiErrorCode.UNAUTHORIZED);
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Long parentId && parentId > 0) {
            return parentId;
        }
        try {
            Long parentId = Long.valueOf(authentication.getName());
            if (parentId < 1) {
                throw new NumberFormatException();
            }
            return parentId;
        } catch (NumberFormatException exception) {
            throw new BusinessException(ReportApiErrorCode.UNAUTHORIZED);
        }
    }
}
