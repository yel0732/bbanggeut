CREATE TABLE report_notification_preference (
    preference_id BIGINT NOT NULL AUTO_INCREMENT,
    parent_id BIGINT NOT NULL,
    enabled BOOLEAN NOT NULL,
    notification_time TIME NOT NULL,
    time_zone VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (preference_id),
    CONSTRAINT uk_report_notification_preference_parent UNIQUE (parent_id)
);

ALTER TABLE report_notification
    ADD COLUMN scheduled_at DATETIME(6) NULL,
    ADD COLUMN delivered_at DATETIME(6) NULL;

UPDATE report_notification
SET scheduled_at = created_at,
    delivered_at = created_at;

CREATE INDEX idx_report_notification_delivery
    ON report_notification (delivered_at, scheduled_at);
