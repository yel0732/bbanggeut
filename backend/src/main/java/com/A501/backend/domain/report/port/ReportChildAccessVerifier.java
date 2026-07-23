package com.A501.backend.domain.report.port;

public interface ReportChildAccessVerifier {

    void verifyOwnership(Long parentId, Long childId);
}
