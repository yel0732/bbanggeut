package com.A501.backend.domain.report.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.A501.backend.domain.report.port.ReportNotificationRecipientPort;

@Component
@Profile("local")
public class LocalReportNotificationRecipientAdapter
        implements ReportNotificationRecipientPort {

    private final Long mockChildId;
    private final Long mockParentId;

    public LocalReportNotificationRecipientAdapter(
            @Value("${app.local.mock-child-id:15}") Long mockChildId,
            @Value("${app.local.mock-child-owner-id:1}") Long mockParentId
    ) {
        this.mockChildId = mockChildId;
        this.mockParentId = mockParentId;
    }

    @Override
    public Long findParentId(Long childId) {
        if (!mockChildId.equals(childId)) {
            throw new IllegalArgumentException(
                    "Notification recipient was not found.");
        }
        return mockParentId;
    }
}
