package com.A501.backend.domain.report.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.report.exception.ReportApiErrorCode;
import com.A501.backend.domain.report.port.ReportChildAccessVerifier;

/**
 * Fail-closed adapter used until the child domain provides its ownership port.
 */
@Component
@Profile("!local")
public class UnavailableReportChildAccessVerifier
        implements ReportChildAccessVerifier {

    @Override
    public void verifyOwnership(Long parentId, Long childId) {
        throw new BusinessException(ReportApiErrorCode.CHILD_NOT_FOUND);
    }
}
