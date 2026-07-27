package com.A501.backend.domain.report.service;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.report.exception.ReportApiErrorCode;
import com.A501.backend.domain.report.port.ReportChildAccessVerifier;
import com.A501.backend.domain.report.port.ReportNotificationRecipientPort;

import lombok.RequiredArgsConstructor;

@Component
@Profile("!local")
@RequiredArgsConstructor
public class JdbcReportChildOwnershipAdapter
        implements ReportChildAccessVerifier, ReportNotificationRecipientPort {

    private static final String FIND_CHILD_SQL = """
            select parent_id, name
            from child
            where child_id = ?
              and deleted_at is null
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void verifyOwnership(Long parentId, Long childId) {
        if (parentId == null || parentId < 1 || childId == null || childId < 1) {
            throw childNotFound();
        }
        List<ReportNotificationRecipient> recipients = findRecipients(childId);
        if (recipients.size() != 1
                || !parentId.equals(recipients.getFirst().parentId())) {
            throw childNotFound();
        }
    }

    @Override
    public ReportNotificationRecipient findRecipient(Long childId) {
        if (childId == null || childId < 1) {
            throw new IllegalArgumentException("Child ID must be positive.");
        }
        List<ReportNotificationRecipient> recipients = findRecipients(childId);
        if (recipients.size() != 1) {
            throw new IllegalArgumentException(
                    "An active child must have exactly one parent owner.");
        }
        return recipients.getFirst();
    }

    private List<ReportNotificationRecipient> findRecipients(Long childId) {
        return jdbcTemplate.query(
                FIND_CHILD_SQL,
                (resultSet, rowNumber) -> new ReportNotificationRecipient(
                        resultSet.getLong("parent_id"),
                        resultSet.getString("name")),
                childId);
    }

    private BusinessException childNotFound() {
        return new BusinessException(ReportApiErrorCode.CHILD_NOT_FOUND);
    }
}
