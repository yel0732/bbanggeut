package com.A501.backend.domain.report.port;

public interface ReportParentAccessTokenVerifier {

    void verify(Long parentId, String parentAccessToken);
}
