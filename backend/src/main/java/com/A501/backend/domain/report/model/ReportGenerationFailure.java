package com.A501.backend.domain.report.model;

import com.A501.backend.domain.report.entity.ReportErrorCode;
import com.A501.backend.domain.report.entity.ReportFailureStage;

public final class ReportGenerationFailure extends RuntimeException {

    private final ReportFailureStage stage;
    private final ReportErrorCode errorCode;
    private final boolean retryable;

    public ReportGenerationFailure(
            ReportFailureStage stage,
            ReportErrorCode errorCode,
            boolean retryable,
            RuntimeException cause
    ) {
        super(cause.getMessage(), cause);
        this.stage = stage;
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    public ReportFailureStage stage() {
        return stage;
    }

    public ReportErrorCode errorCode() {
        return errorCode;
    }

    public boolean retryable() {
        return retryable;
    }

    public RuntimeException originalException() {
        return (RuntimeException) getCause();
    }
}
