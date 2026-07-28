ALTER TABLE conversation_session
    ADD COLUMN current_session_epoch_started_at DATETIME(6) NULL
        AFTER current_session_epoch;

ALTER TABLE conversation_turn
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0
        AFTER interruption_id;

CREATE TABLE conversation_event (
    event_id            VARCHAR(80)  NOT NULL,
    redis_record_id     VARCHAR(80)  NOT NULL,
    conversation_id     VARCHAR(40)  NOT NULL,
    realtime_session_id VARCHAR(80)  NOT NULL,
    session_epoch       INT          NOT NULL,
    event_type          VARCHAR(40)  NOT NULL,
    turn_id             VARCHAR(40)  NULL,
    occurred_at         DATETIME(6)  NOT NULL,
    processed_at        DATETIME(6)  NOT NULL,
    processing_result   VARCHAR(30)  NOT NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (event_id),
    CONSTRAINT uk_conversation_event_redis_record UNIQUE (redis_record_id)
) ENGINE=InnoDB;

CREATE INDEX idx_conversation_event_conversation_epoch
    ON conversation_event (conversation_id, session_epoch);
CREATE INDEX idx_conversation_event_turn
    ON conversation_event (conversation_id, turn_id);

CREATE TABLE conversation_event_failure (
    conversation_event_failure_id BIGINT        NOT NULL AUTO_INCREMENT,
    redis_record_id               VARCHAR(80)   NOT NULL,
    event_id                      VARCHAR(80)   NULL,
    conversation_id               VARCHAR(40)   NULL,
    event_type                    VARCHAR(40)   NULL,
    delivery_count                BIGINT        NOT NULL,
    failed_at                     DATETIME(6)   NOT NULL,
    failure_reason                VARCHAR(1000) NOT NULL,
    created_at                    DATETIME(6)   NOT NULL,
    updated_at                    DATETIME(6)   NOT NULL,
    PRIMARY KEY (conversation_event_failure_id),
    CONSTRAINT uk_conversation_event_failure_record UNIQUE (redis_record_id)
) ENGINE=InnoDB;

CREATE INDEX idx_conversation_event_failure_record
    ON conversation_event_failure (redis_record_id);
