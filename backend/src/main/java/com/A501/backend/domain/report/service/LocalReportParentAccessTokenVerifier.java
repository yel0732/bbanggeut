package com.A501.backend.domain.report.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.report.exception.ReportApiErrorCode;
import com.A501.backend.domain.report.port.ReportParentAccessTokenVerifier;

@Component
@Profile("local")
public class LocalReportParentAccessTokenVerifier
        implements ReportParentAccessTokenVerifier {

    private final String mockToken;

    public LocalReportParentAccessTokenVerifier(
            @Value("${app.local.mock-parent-access-token:local-parent-token}")
            String mockToken
    ) {
        this.mockToken = mockToken;
    }

    @Override
    public void verify(Long parentId, String parentAccessToken) {
        if (!mockToken.equals(parentAccessToken)) {
            throw new BusinessException(
                    ReportApiErrorCode.INVALID_PARENT_ACCESS_TOKEN);
        }
    }
}
