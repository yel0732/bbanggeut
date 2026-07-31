CREATE TABLE recycling_game_result (
    game_session_id BINARY(16)  NOT NULL,
    rules_version   VARCHAR(40) NOT NULL,
    successful_count INT        NOT NULL,
    wrong_count     INT         NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (game_session_id),
    CONSTRAINT fk_recycling_result_game_session
        FOREIGN KEY (game_session_id) REFERENCES game_session (game_session_id),
    CONSTRAINT ck_recycling_result_successful_count
        CHECK (successful_count >= 0),
    CONSTRAINT ck_recycling_result_wrong_count
        CHECK (wrong_count >= 0)
) ENGINE=InnoDB;
