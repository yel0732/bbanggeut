package com.A501.backend.domain.report.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.report.exception.ReportApiErrorCode;
import com.A501.backend.domain.report.port.ReportChildAccessVerifier;

@Component
@Profile("local")
public class LocalReportChildAccessVerifier
        implements ReportChildAccessVerifier {

    private final Long mockChildId;
    private final Long mockOwnerId;

    public LocalReportChildAccessVerifier(
            @Value("${app.local.mock-child-id:15}") Long mockChildId,
            @Value("${app.local.mock-child-owner-id:1}") Long mockOwnerId
    ) {
        this.mockChildId = mockChildId;
        this.mockOwnerId = mockOwnerId;
    }

    @Override
    public void verifyOwnership(Long parentId, Long childId) {
        if (!mockChildId.equals(childId) || !mockOwnerId.equals(parentId)) {
            throw new BusinessException(ReportApiErrorCode.CHILD_NOT_FOUND);
        }
    }
}
