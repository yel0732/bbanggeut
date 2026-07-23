package com.A501.backend.domain.report.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.report.exception.ReportApiErrorCode;
import com.A501.backend.domain.report.port.ReportCurrentParentProvider;

@Component
@Profile("local")
public class LocalReportCurrentParentProvider
        implements ReportCurrentParentProvider {

    private final Long defaultParentId;

    public LocalReportCurrentParentProvider(
            @Value("${app.local.mock-principal-id:1}") Long defaultParentId
    ) {
        this.defaultParentId = defaultParentId;
    }

    @Override
    public Long getCurrentParentId() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return defaultParentId;
        }
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException exception) {
            throw new BusinessException(ReportApiErrorCode.UNAUTHORIZED);
        }
    }
}
