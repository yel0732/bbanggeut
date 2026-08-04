ALTER TABLE report_notification_preference
    ADD COLUMN notification_minute SMALLINT NULL;

-- Existing Asia/Seoul values were persisted nine hours behind because the
-- JDBC connection used UTC for a wall-clock TIME value. Restore those values
-- once while converting to a timezone-neutral minute-of-day representation.
UPDATE report_notification_preference
SET notification_minute = MOD(
        HOUR(notification_time) * 60
            + MINUTE(notification_time)
            + CASE WHEN time_zone = 'Asia/Seoul' THEN 540 ELSE 0 END,
        1440
    );

ALTER TABLE report_notification_preference
    MODIFY COLUMN notification_minute SMALLINT NOT NULL,
    ADD CONSTRAINT chk_report_notification_minute
        CHECK (notification_minute BETWEEN 0 AND 1439),
    DROP COLUMN notification_time;
