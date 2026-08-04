ALTER TABLE parent
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'PARENT' AFTER provider_id,
    ADD CONSTRAINT chk_parent_role CHECK (role IN ('PARENT', 'ADMIN'));

CREATE TABLE admin_report_generation_audit (
    audit_id        BIGINT      NOT NULL AUTO_INCREMENT,
    admin_parent_id BIGINT      NOT NULL,
    child_id        BIGINT      NOT NULL,
    report_id       BIGINT      NOT NULL,
    report_date     DATE        NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (audit_id),
    INDEX idx_admin_report_audit_admin_created (admin_parent_id, created_at),
    INDEX idx_admin_report_audit_child_date (child_id, report_date)
) ENGINE=InnoDB;
