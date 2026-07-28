-- Initial production schema for MySQL 8.x.
-- Application tables are based on the current JPA entities and backend/SCHEMA.

CREATE TABLE parent (
    parent_id           BIGINT       NOT NULL AUTO_INCREMENT,
    email               VARCHAR(255) NOT NULL,
    password_hash       VARCHAR(255) NULL,
    birth_date          DATE         NOT NULL,
    provider            VARCHAR(20)  NOT NULL,
    provider_id         VARCHAR(255) NULL,
    pin_hash            VARCHAR(255) NULL,
    pin_failed_attempts INT          NOT NULL DEFAULT 0,
    pin_locked_until    DATETIME(6)  NULL,
    pin_set_at          DATETIME(6)  NULL,
    deleted_at          DATETIME(6)  NULL,
    profile_image_num   INT          NOT NULL DEFAULT 1,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (parent_id),
    CONSTRAINT uk_parent_email UNIQUE (email),
    CONSTRAINT uk_parent_provider_id UNIQUE (provider_id)
) ENGINE=InnoDB;

CREATE TABLE refresh_token (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    parent_id   BIGINT       NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL,
    expires_at  DATETIME(6)  NOT NULL,
    revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
    remember_me BOOLEAN      NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_token_parent
        FOREIGN KEY (parent_id) REFERENCES parent (parent_id)
) ENGINE=InnoDB;

CREATE TABLE child (
    child_id          BIGINT       NOT NULL AUTO_INCREMENT,
    parent_id         BIGINT       NOT NULL,
    name              VARCHAR(15)  NOT NULL,
    profile_image_num INT          NOT NULL,
    deleted_at        DATETIME(6)  NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (child_id)
) ENGINE=InnoDB;

CREATE INDEX idx_child_parent_active ON child (parent_id, deleted_at);

CREATE TABLE conversation_session (
    conversation_id        VARCHAR(40) NOT NULL,
    child_id               BIGINT      NOT NULL,
    status                 VARCHAR(20) NOT NULL,
    current_topic          VARCHAR(20) NOT NULL,
    question_count         INT         NOT NULL,
    max_question_count     INT         NOT NULL,
    current_session_epoch  INT         NOT NULL,
    started_at             DATETIME(6) NULL,
    completed_at           DATETIME(6) NULL,
    termination_reason     VARCHAR(40) NULL,
    version                BIGINT      NOT NULL DEFAULT 0,
    created_at             DATETIME(6) NOT NULL,
    updated_at             DATETIME(6) NOT NULL,
    PRIMARY KEY (conversation_id)
) ENGINE=InnoDB;

CREATE INDEX idx_conversation_session_child_status
    ON conversation_session (child_id, status);
CREATE INDEX idx_conversation_session_status_updated
    ON conversation_session (status, updated_at);

CREATE TABLE conversation_topic_state (
    conversation_topic_id VARCHAR(50) NOT NULL,
    conversation_id       VARCHAR(40) NOT NULL,
    topic_type            VARCHAR(20) NOT NULL,
    topic_order           INT         NOT NULL,
    status                VARCHAR(20) NOT NULL,
    question_count        INT         NOT NULL,
    max_question_count    INT         NOT NULL,
    slots_json            TEXT        NOT NULL,
    context_json          TEXT        NOT NULL,
    completion_reason     VARCHAR(40) NULL,
    started_at            DATETIME(6) NULL,
    completed_at          DATETIME(6) NULL,
    version               BIGINT      NOT NULL DEFAULT 0,
    created_at            DATETIME(6) NOT NULL,
    updated_at            DATETIME(6) NOT NULL,
    PRIMARY KEY (conversation_topic_id),
    CONSTRAINT uk_conversation_topic_type UNIQUE (conversation_id, topic_type),
    CONSTRAINT uk_conversation_topic_order UNIQUE (conversation_id, topic_order)
) ENGINE=InnoDB;

CREATE INDEX idx_conversation_topic_status
    ON conversation_topic_state (conversation_id, status);

CREATE TABLE conversation_game_session (
    conversation_game_session_id VARCHAR(80) NOT NULL,
    conversation_id              VARCHAR(40) NOT NULL,
    game_session_id              BINARY(16)  NOT NULL,
    game_order                   INT         NOT NULL,
    created_at                   DATETIME(6) NOT NULL,
    updated_at                   DATETIME(6) NOT NULL,
    PRIMARY KEY (conversation_game_session_id),
    CONSTRAINT uk_conversation_game_session UNIQUE (conversation_id, game_session_id),
    CONSTRAINT uk_conversation_game_order UNIQUE (conversation_id, game_order)
) ENGINE=InnoDB;

CREATE TABLE game_session (
    game_session_id       BINARY(16)  NOT NULL,
    child_id              BIGINT      NOT NULL,
    game_id               BIGINT      NOT NULL,
    client_play_id        BINARY(16)  NOT NULL,
    difficulty_code       VARCHAR(30) NULL,
    selected_at           DATETIME(6) NOT NULL,
    play_started_at       DATETIME(6) NOT NULL,
    completed_at          DATETIME(6) NOT NULL,
    play_duration_seconds INT         NOT NULL,
    score                 INT         NOT NULL,
    created_at            DATETIME(6) NOT NULL,
    PRIMARY KEY (game_session_id),
    CONSTRAINT uk_game_session_client_play_id UNIQUE (client_play_id)
) ENGINE=InnoDB;

CREATE TABLE game_rule_version (
    rule_version_id         BIGINT         NOT NULL AUTO_INCREMENT,
    version_code            VARCHAR(40)    NOT NULL,
    planned_duration_sec    INT            NOT NULL,
    slice_speed_threshold   DECIMAL(10, 4) NOT NULL,
    fruit_spawn_interval_ms INT            NOT NULL,
    max_visible_fruit       INT            NOT NULL,
    active                  BOOLEAN        NOT NULL,
    created_at              DATETIME(6)    NOT NULL,
    updated_at              DATETIME(6)    NOT NULL,
    PRIMARY KEY (rule_version_id),
    CONSTRAINT uk_game_rule_version_code UNIQUE (version_code)
) ENGINE=InnoDB;

CREATE TABLE fruit_type (
    fruit_type_id   BIGINT       NOT NULL AUTO_INCREMENT,
    fruit_code      VARCHAR(40)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    default_size_px INT          NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (fruit_type_id),
    CONSTRAINT uk_fruit_type_code UNIQUE (fruit_code)
) ENGINE=InnoDB;

CREATE TABLE fruit_score_config (
    rule_version_id BIGINT NOT NULL,
    fruit_type_id   BIGINT NOT NULL,
    score           INT    NOT NULL,
    PRIMARY KEY (rule_version_id, fruit_type_id),
    CONSTRAINT fk_fruit_score_rule
        FOREIGN KEY (rule_version_id) REFERENCES game_rule_version (rule_version_id),
    CONSTRAINT fk_fruit_score_type
        FOREIGN KEY (fruit_type_id) REFERENCES fruit_type (fruit_type_id)
) ENGINE=InnoDB;

CREATE TABLE fruit_ninja_result (
    game_session_id    BINARY(16)  NOT NULL,
    rules_version      VARCHAR(40) NOT NULL,
    total_fruit_count  INT         NOT NULL,
    sliced_fruit_count INT         NOT NULL,
    created_at         DATETIME(6) NOT NULL,
    PRIMARY KEY (game_session_id)
) ENGINE=InnoDB;

CREATE TABLE fruit_ninja_fruit_result (
    fruit_result_id BINARY(16)  NOT NULL,
    game_session_id BINARY(16)  NOT NULL,
    fruit_code      VARCHAR(40) NOT NULL,
    generated_count INT         NOT NULL,
    sliced_count    INT         NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (fruit_result_id),
    CONSTRAINT uk_fruit_ninja_fruit_session_code UNIQUE (game_session_id, fruit_code)
) ENGINE=InnoDB;

CREATE TABLE card_round (
    card_round_id   BINARY(16)  NOT NULL,
    game_session_id BINARY(16)  NOT NULL,
    round_no        INT         NOT NULL,
    status          VARCHAR(20) NOT NULL,
    comparison_count INT        NOT NULL,
    wrong_count     INT         NOT NULL,
    final_grade     VARCHAR(20) NULL,
    solve_time_ms   BIGINT      NULL,
    created_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (card_round_id),
    CONSTRAINT uk_card_round_session_round UNIQUE (game_session_id, round_no),
    CONSTRAINT fk_card_round_game_session
        FOREIGN KEY (game_session_id) REFERENCES game_session (game_session_id)
) ENGINE=InnoDB;

CREATE INDEX idx_card_round_game_session ON card_round (game_session_id);

CREATE TABLE conversation_turn (
    turn_id             VARCHAR(40)  NOT NULL,
    conversation_id     VARCHAR(40)  NOT NULL,
    turn_seq            INT          NOT NULL,
    speaker             VARCHAR(10)  NOT NULL,
    topic_type          VARCHAR(20)  NOT NULL,
    content             TEXT         NOT NULL,
    question_text       TEXT         NULL,
    status              VARCHAR(20)  NOT NULL,
    response_id         VARCHAR(40)  NULL,
    stt_confidence      DECIMAL(5,4) NULL,
    started_at          DATETIME(6)  NULL,
    completed_at        DATETIME(6)  NULL,
    interrupted_at      DATETIME(6)  NULL,
    interruption_reason VARCHAR(40)  NULL,
    interruption_id     VARCHAR(40)  NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (turn_id),
    CONSTRAINT uk_conversation_turn_seq UNIQUE (conversation_id, turn_seq)
) ENGINE=InnoDB;

CREATE INDEX idx_conversation_turn_response_id ON conversation_turn (response_id);
CREATE INDEX idx_conversation_turn_interruption_id ON conversation_turn (interruption_id);

CREATE TABLE daily_report (
    report_id             BIGINT        NOT NULL AUTO_INCREMENT,
    child_id              BIGINT        NOT NULL,
    report_date           DATE          NOT NULL,
    period_started_at     DATETIME(6)   NOT NULL,
    period_ended_at       DATETIME(6)   NOT NULL,
    generation_status     VARCHAR(20)   NOT NULL,
    retry_count           INT           NOT NULL DEFAULT 0,
    processing_started_at DATETIME(6)   NULL,
    completed_at          DATETIME(6)   NULL,
    failed_at             DATETIME(6)   NULL,
    failure_reason        VARCHAR(1000) NULL,
    created_at            DATETIME(6)   NOT NULL,
    updated_at            DATETIME(6)   NOT NULL,
    PRIMARY KEY (report_id),
    CONSTRAINT uk_daily_report_child_date UNIQUE (child_id, report_date)
) ENGINE=InnoDB;

CREATE INDEX idx_daily_report_status_processing
    ON daily_report (generation_status, processing_started_at);

CREATE TABLE report_generation_attempt (
    attempt_id     BIGINT        NOT NULL AUTO_INCREMENT,
    report_id      BIGINT        NOT NULL,
    attempt_number INT           NOT NULL,
    attempt_status VARCHAR(20)   NOT NULL,
    started_at     DATETIME(6)   NOT NULL,
    ended_at       DATETIME(6)   NULL,
    failure_stage  VARCHAR(40)   NULL,
    error_code     VARCHAR(60)   NULL,
    failure_reason VARCHAR(1000) NULL,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    PRIMARY KEY (attempt_id),
    CONSTRAINT uk_report_generation_attempt_number UNIQUE (report_id, attempt_number)
) ENGINE=InnoDB;

CREATE INDEX idx_report_generation_attempt_report
    ON report_generation_attempt (report_id, attempt_number);

CREATE TABLE report_manual_regeneration (
    regeneration_id         BIGINT        NOT NULL AUTO_INCREMENT,
    report_id               BIGINT        NOT NULL,
    child_id                BIGINT        NOT NULL,
    parent_id               BIGINT        NOT NULL,
    idempotency_key         VARCHAR(100)  NOT NULL,
    regeneration_status     VARCHAR(20)   NOT NULL,
    requested_at            DATETIME(6)   NOT NULL,
    previous_failure_reason VARCHAR(1000) NULL,
    started_at              DATETIME(6)   NULL,
    ended_at                DATETIME(6)   NULL,
    failure_reason          VARCHAR(1000) NULL,
    created_at              DATETIME(6)   NOT NULL,
    updated_at              DATETIME(6)   NOT NULL,
    PRIMARY KEY (regeneration_id),
    CONSTRAINT uk_report_manual_regeneration_parent_key UNIQUE (parent_id, idempotency_key)
) ENGINE=InnoDB;

CREATE INDEX idx_report_manual_regeneration_report
    ON report_manual_regeneration (report_id, requested_at);

CREATE TABLE report_notification (
    notification_id     BIGINT       NOT NULL AUTO_INCREMENT,
    parent_id           BIGINT       NOT NULL,
    report_id           BIGINT       NOT NULL,
    child_id            BIGINT       NOT NULL,
    report_date         DATE         NOT NULL,
    notification_type   VARCHAR(30)  NOT NULL,
    notification_status VARCHAR(20)  NOT NULL,
    message             VARCHAR(200) NOT NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (notification_id),
    CONSTRAINT uk_report_notification_report_type UNIQUE (report_id, notification_type)
) ENGINE=InnoDB;

CREATE INDEX idx_report_notification_parent_created
    ON report_notification (parent_id, created_at);
CREATE INDEX idx_report_notification_report ON report_notification (report_id);

CREATE TABLE report_notification_failure (
    notification_failure_id BIGINT        NOT NULL AUTO_INCREMENT,
    report_id               BIGINT        NOT NULL,
    notification_type       VARCHAR(30)   NOT NULL,
    failed_at               DATETIME(6)   NOT NULL,
    failure_reason          VARCHAR(1000) NOT NULL,
    created_at              DATETIME(6)   NOT NULL,
    updated_at              DATETIME(6)   NOT NULL,
    PRIMARY KEY (notification_failure_id)
) ENGINE=InnoDB;

CREATE INDEX idx_report_notification_failure_report
    ON report_notification_failure (report_id, failed_at);

CREATE TABLE report_game_result (
    report_game_result_id BIGINT       NOT NULL AUTO_INCREMENT,
    report_id             BIGINT       NOT NULL,
    game_session_id       BINARY(16)   NOT NULL,
    game_id               BIGINT       NOT NULL,
    game_code             VARCHAR(40)  NOT NULL,
    game_name             VARCHAR(100) NOT NULL,
    completed_at          DATETIME(6)  NOT NULL,
    play_duration_seconds INT          NOT NULL,
    completed             BOOLEAN      NOT NULL,
    score                 INT          NOT NULL,
    result_metrics        TEXT         NULL,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    PRIMARY KEY (report_game_result_id),
    CONSTRAINT uk_report_game_result_report_session UNIQUE (report_id, game_session_id)
) ENGINE=InnoDB;

CREATE TABLE report_conversation_summary (
    report_conversation_summary_id BIGINT       NOT NULL AUTO_INCREMENT,
    report_id                      BIGINT       NOT NULL,
    source_conversation_ids        TEXT         NOT NULL,
    diary_title                    VARCHAR(200) NOT NULL,
    diary_content                  TEXT         NOT NULL,
    main_events                    TEXT         NOT NULL,
    emotions                       TEXT         NOT NULL,
    interests                      TEXT         NOT NULL,
    model_version                  VARCHAR(100) NULL,
    prompt_version                 VARCHAR(100) NULL,
    created_at                     DATETIME(6)  NOT NULL,
    updated_at                     DATETIME(6)  NOT NULL,
    PRIMARY KEY (report_conversation_summary_id),
    CONSTRAINT uk_report_conversation_summary_report UNIQUE (report_id)
) ENGINE=InnoDB;

CREATE TABLE child_diary (
    child_diary_id    BIGINT       NOT NULL AUTO_INCREMENT,
    child_id          BIGINT       NOT NULL,
    conversation_id   VARCHAR(40)  NOT NULL,
    diary_date        DATE         NOT NULL,
    title             VARCHAR(20)  NOT NULL,
    lines_json        TEXT         NOT NULL,
    generation_method VARCHAR(20)  NOT NULL,
    model_version     VARCHAR(100) NULL,
    prompt_version    VARCHAR(100) NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (child_diary_id),
    CONSTRAINT uk_child_diary_conversation UNIQUE (conversation_id)
) ENGINE=InnoDB;

CREATE INDEX idx_child_diary_child_date ON child_diary (child_id, diary_date);

-- Spring Batch 6.0.4 MySQL metadata schema.

CREATE TABLE BATCH_JOB_INSTANCE (
    JOB_INSTANCE_ID BIGINT       NOT NULL PRIMARY KEY,
    VERSION         BIGINT,
    JOB_NAME        VARCHAR(100) NOT NULL,
    JOB_KEY         VARCHAR(32)  NOT NULL,
    CONSTRAINT JOB_INST_UN UNIQUE (JOB_NAME, JOB_KEY)
) ENGINE=InnoDB;

CREATE TABLE BATCH_JOB_EXECUTION (
    JOB_EXECUTION_ID BIGINT NOT NULL PRIMARY KEY,
    VERSION          BIGINT,
    JOB_INSTANCE_ID  BIGINT NOT NULL,
    CREATE_TIME      DATETIME(6) NOT NULL,
    START_TIME       DATETIME(6) DEFAULT NULL,
    END_TIME         DATETIME(6) DEFAULT NULL,
    STATUS           VARCHAR(10),
    EXIT_CODE        VARCHAR(2500),
    EXIT_MESSAGE     VARCHAR(2500),
    LAST_UPDATED     DATETIME(6),
    CONSTRAINT JOB_INST_EXEC_FK FOREIGN KEY (JOB_INSTANCE_ID)
        REFERENCES BATCH_JOB_INSTANCE (JOB_INSTANCE_ID)
) ENGINE=InnoDB;

CREATE TABLE BATCH_JOB_EXECUTION_PARAMS (
    JOB_EXECUTION_ID BIGINT       NOT NULL,
    PARAMETER_NAME   VARCHAR(100) NOT NULL,
    PARAMETER_TYPE   VARCHAR(100) NOT NULL,
    PARAMETER_VALUE  VARCHAR(2500),
    IDENTIFYING      CHAR(1)      NOT NULL,
    CONSTRAINT JOB_EXEC_PARAMS_FK FOREIGN KEY (JOB_EXECUTION_ID)
        REFERENCES BATCH_JOB_EXECUTION (JOB_EXECUTION_ID)
) ENGINE=InnoDB;

CREATE TABLE BATCH_STEP_EXECUTION (
    STEP_EXECUTION_ID  BIGINT       NOT NULL PRIMARY KEY,
    VERSION            BIGINT       NOT NULL,
    STEP_NAME          VARCHAR(100) NOT NULL,
    JOB_EXECUTION_ID   BIGINT       NOT NULL,
    CREATE_TIME        DATETIME(6)  NOT NULL,
    START_TIME         DATETIME(6) DEFAULT NULL,
    END_TIME           DATETIME(6) DEFAULT NULL,
    STATUS             VARCHAR(10),
    COMMIT_COUNT       BIGINT,
    READ_COUNT         BIGINT,
    FILTER_COUNT       BIGINT,
    WRITE_COUNT        BIGINT,
    READ_SKIP_COUNT    BIGINT,
    WRITE_SKIP_COUNT   BIGINT,
    PROCESS_SKIP_COUNT BIGINT,
    ROLLBACK_COUNT     BIGINT,
    EXIT_CODE          VARCHAR(2500),
    EXIT_MESSAGE       VARCHAR(2500),
    LAST_UPDATED       DATETIME(6),
    CONSTRAINT JOB_EXEC_STEP_FK FOREIGN KEY (JOB_EXECUTION_ID)
        REFERENCES BATCH_JOB_EXECUTION (JOB_EXECUTION_ID)
) ENGINE=InnoDB;

CREATE TABLE BATCH_STEP_EXECUTION_CONTEXT (
    STEP_EXECUTION_ID BIGINT        NOT NULL PRIMARY KEY,
    SHORT_CONTEXT     VARCHAR(2500) NOT NULL,
    SERIALIZED_CONTEXT TEXT,
    CONSTRAINT STEP_EXEC_CTX_FK FOREIGN KEY (STEP_EXECUTION_ID)
        REFERENCES BATCH_STEP_EXECUTION (STEP_EXECUTION_ID)
) ENGINE=InnoDB;

CREATE TABLE BATCH_JOB_EXECUTION_CONTEXT (
    JOB_EXECUTION_ID BIGINT        NOT NULL PRIMARY KEY,
    SHORT_CONTEXT    VARCHAR(2500) NOT NULL,
    SERIALIZED_CONTEXT TEXT,
    CONSTRAINT JOB_EXEC_CTX_FK FOREIGN KEY (JOB_EXECUTION_ID)
        REFERENCES BATCH_JOB_EXECUTION (JOB_EXECUTION_ID)
) ENGINE=InnoDB;

CREATE TABLE BATCH_STEP_EXECUTION_SEQ (
    ID         BIGINT  NOT NULL,
    UNIQUE_KEY CHAR(1) NOT NULL,
    CONSTRAINT BATCH_STEP_EXECUTION_SEQ_UN UNIQUE (UNIQUE_KEY)
) ENGINE=InnoDB;

INSERT INTO BATCH_STEP_EXECUTION_SEQ (ID, UNIQUE_KEY)
SELECT * FROM (SELECT 0 AS ID, '0' AS UNIQUE_KEY) AS tmp
WHERE NOT EXISTS (SELECT * FROM BATCH_STEP_EXECUTION_SEQ);

CREATE TABLE BATCH_JOB_EXECUTION_SEQ (
    ID         BIGINT  NOT NULL,
    UNIQUE_KEY CHAR(1) NOT NULL,
    CONSTRAINT BATCH_JOB_EXECUTION_SEQ_UN UNIQUE (UNIQUE_KEY)
) ENGINE=InnoDB;

INSERT INTO BATCH_JOB_EXECUTION_SEQ (ID, UNIQUE_KEY)
SELECT * FROM (SELECT 0 AS ID, '0' AS UNIQUE_KEY) AS tmp
WHERE NOT EXISTS (SELECT * FROM BATCH_JOB_EXECUTION_SEQ);

CREATE TABLE BATCH_JOB_INSTANCE_SEQ (
    ID         BIGINT  NOT NULL,
    UNIQUE_KEY CHAR(1) NOT NULL,
    CONSTRAINT BATCH_JOB_INSTANCE_SEQ_UN UNIQUE (UNIQUE_KEY)
) ENGINE=InnoDB;

INSERT INTO BATCH_JOB_INSTANCE_SEQ (ID, UNIQUE_KEY)
SELECT * FROM (SELECT 0 AS ID, '0' AS UNIQUE_KEY) AS tmp
WHERE NOT EXISTS (SELECT * FROM BATCH_JOB_INSTANCE_SEQ);
