package com.A501.backend.domain.report.entity;

public enum ReportFailureStage {
    PORT_CONNECTION,
    GAME_QUERY,
    CONVERSATION_SUMMARY,
    SOURCE_VALIDATION,
    SNAPSHOT_PERSISTENCE,
    UNKNOWN
}
