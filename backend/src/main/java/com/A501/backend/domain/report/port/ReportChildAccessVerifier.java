package com.A501.backend.domain.report.port;

public interface ReportChildAccessVerifier {

    /**
     * Verifies that the child exists, is active, and belongs to the parent.
     * Every failure must be exposed as CHILD_NOT_FOUND to prevent IDOR.
     */
    void verifyOwnership(Long parentId, Long childId);
}
