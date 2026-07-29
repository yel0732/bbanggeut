CREATE TABLE game (
    game_id    BIGINT       NOT NULL,
    game_code  VARCHAR(40)  NOT NULL,
    game_name  VARCHAR(100) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (game_id),
    CONSTRAINT uk_game_code UNIQUE (game_code)
) ENGINE=InnoDB;

INSERT INTO game (
    game_id,
    game_code,
    game_name,
    created_at,
    updated_at
) VALUES
    (7, 'DOBBLE', '그림 맞추기', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (8, 'FRUIT_NINJA', '과일 자르기', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6));

ALTER TABLE game_session
    ADD CONSTRAINT fk_game_session_game
        FOREIGN KEY (game_id) REFERENCES game (game_id);
