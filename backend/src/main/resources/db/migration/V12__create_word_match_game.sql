INSERT INTO game (game_id, game_code, game_name, created_at, updated_at)
VALUES (10, 'WORD_MATCH', '낱말 맞추기', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6));

CREATE TABLE word_match_result (
    game_session_id       BINARY(16)  NOT NULL,
    rules_version         VARCHAR(40) NOT NULL,
    solved_word_count     INT         NOT NULL,
    incomplete_word_count INT         NOT NULL,
    wrong_count           INT         NOT NULL,
    hint_used_count       INT         NOT NULL,
    created_at            DATETIME(6) NOT NULL,
    PRIMARY KEY (game_session_id),
    CONSTRAINT fk_word_match_result_session
        FOREIGN KEY (game_session_id) REFERENCES game_session (game_session_id),
    CONSTRAINT ck_word_match_result_counts
        CHECK (solved_word_count >= 0 AND incomplete_word_count >= 0
            AND wrong_count >= 0 AND hint_used_count >= 0)
) ENGINE=InnoDB;

CREATE TABLE word_match_problem_result (
    word_match_problem_result_id BIGINT      NOT NULL AUTO_INCREMENT,
    game_session_id              BINARY(16)  NOT NULL,
    problem_code                 VARCHAR(40) NOT NULL,
    question_order               INT         NOT NULL,
    word_length                  INT         NOT NULL,
    status                       VARCHAR(20) NOT NULL,
    wrong_count                  INT         NOT NULL,
    hint_used                    BOOLEAN     NOT NULL,
    earned_score                 INT         NOT NULL,
    solve_time_ms                BIGINT      NULL,
    created_at                   DATETIME(6) NOT NULL,
    PRIMARY KEY (word_match_problem_result_id),
    CONSTRAINT uk_word_match_result_order UNIQUE (game_session_id, question_order),
    CONSTRAINT uk_word_match_result_problem UNIQUE (game_session_id, problem_code),
    CONSTRAINT fk_word_match_problem_result_session
        FOREIGN KEY (game_session_id) REFERENCES game_session (game_session_id),
    CONSTRAINT ck_word_match_problem_result_status
        CHECK (status IN ('COMPLETED', 'INCOMPLETE')),
    CONSTRAINT ck_word_match_problem_result_values
        CHECK (question_order >= 1 AND word_length BETWEEN 2 AND 4
            AND wrong_count >= 0 AND earned_score >= 0
            AND (solve_time_ms IS NULL OR solve_time_ms > 0))
) ENGINE=InnoDB;
