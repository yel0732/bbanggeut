ALTER TABLE report_notification
    ADD COLUMN read_at DATETIME(6) NULL;

CREATE INDEX idx_report_notification_unread
    ON report_notification (parent_id, notification_status, scheduled_at);
