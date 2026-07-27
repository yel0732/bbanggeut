package com.A501.backend.domain.report.port;

public interface ReportNotificationRecipientPort {

    ReportNotificationRecipient findRecipient(Long childId);

    record ReportNotificationRecipient(Long parentId, String childName) {

        public ReportNotificationRecipient {
            if (parentId == null || parentId < 1) {
                throw new IllegalArgumentException("Parent ID must be positive.");
            }
            if (childName == null || childName.isBlank()) {
                throw new IllegalArgumentException("Child name is required.");
            }
        }
    }
}
