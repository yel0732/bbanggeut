package com.A501.backend.domain.report.service;

public class ReportAiUnavailableException extends RuntimeException {

    public ReportAiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public ReportAiUnavailableException(String message) {
        super(message);
    }
}
