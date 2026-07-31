INSERT INTO game (
    game_id,
    game_code,
    game_name,
    created_at,
    updated_at
) VALUES (
    9,
    'RECYCLING',
    '분리수거',
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
);

CREATE TABLE recycling_rule_version (
    recycling_rule_version_id BIGINT      NOT NULL AUTO_INCREMENT,
    version_code              VARCHAR(40) NOT NULL,
    planned_duration_sec      INT         NOT NULL,
    trash_spawn_interval_ms   INT         NOT NULL,
    trash_movement_duration_ms INT        NOT NULL,
    max_visible_trash         INT         NOT NULL,
    score_per_correct         INT         NOT NULL,
    wrong_guide_duration_ms   INT         NOT NULL,
    active                    BOOLEAN     NOT NULL,
    created_at                DATETIME(6) NOT NULL,
    updated_at                DATETIME(6) NOT NULL,
    PRIMARY KEY (recycling_rule_version_id),
    CONSTRAINT uk_recycling_rule_version_code UNIQUE (version_code)
) ENGINE=InnoDB;

CREATE TABLE recycling_item (
    recycling_item_id BIGINT       NOT NULL AUTO_INCREMENT,
    game_id           BIGINT       NOT NULL,
    item_code         VARCHAR(40)  NOT NULL,
    item_name         VARCHAR(100) NOT NULL,
    image_url         VARCHAR(512) NOT NULL,
    category          VARCHAR(20)  NOT NULL,
    guide_message     VARCHAR(255) NOT NULL,
    active            BOOLEAN      NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (recycling_item_id),
    CONSTRAINT uk_recycling_item_code UNIQUE (item_code),
    CONSTRAINT fk_recycling_item_game
        FOREIGN KEY (game_id) REFERENCES game (game_id),
    CONSTRAINT ck_recycling_item_category
        CHECK (category IN ('CAN', 'PLASTIC', 'PAPER', 'GLASS'))
) ENGINE=InnoDB;

CREATE INDEX idx_recycling_item_active
    ON recycling_item (active, recycling_item_id);

INSERT INTO recycling_rule_version (
    version_code,
    planned_duration_sec,
    trash_spawn_interval_ms,
    trash_movement_duration_ms,
    max_visible_trash,
    score_per_correct,
    wrong_guide_duration_ms,
    active,
    created_at,
    updated_at
) VALUES (
    'RECYCLING_V1',
    60,
    2000,
    10000,
    5,
    100,
    2500,
    TRUE,
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
);

INSERT INTO recycling_item (
    game_id,
    item_code,
    item_name,
    image_url,
    category,
    guide_message,
    active,
    created_at,
    updated_at
) VALUES
    (9, 'BEVERAGE_CAN', '음료수 캔', '/assets/recycling/items/beverage-can.png', 'CAN',
     '기억해 두자! 음료수 캔은 캔 통에 넣으면 돼!', TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (9, 'TIN_CAN', '통조림 캔', '/assets/recycling/items/tin-can.png', 'CAN',
     '기억해 두자! 통조림 캔은 캔 통에 넣으면 돼!', TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (9, 'PLASTIC_BOTTLE', '페트병', '/assets/recycling/items/plastic-bottle.png', 'PLASTIC',
     '기억해 두자! 페트병은 플라스틱 통에 넣으면 돼!', TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (9, 'PLASTIC_CONTAINER', '플라스틱 용기', '/assets/recycling/items/plastic-container.png', 'PLASTIC',
     '기억해 두자! 플라스틱 용기는 플라스틱 통에 넣으면 돼!', TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (9, 'NEWSPAPER', '신문', '/assets/recycling/items/newspaper.png', 'PAPER',
     '기억해 두자! 신문은 종이 통에 넣으면 돼!', TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (9, 'PAPER_BOX', '종이상자', '/assets/recycling/items/paper-box.png', 'PAPER',
     '기억해 두자! 종이상자는 종이 통에 넣으면 돼!', TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (9, 'PAPER_BAG', '종이봉투', '/assets/recycling/items/paper-bag.png', 'PAPER',
     '기억해 두자! 종이봉투는 종이 통에 넣으면 돼!', TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (9, 'GLASS_BOTTLE', '유리병', '/assets/recycling/items/glass-bottle.png', 'GLASS',
     '기억해 두자! 유리병은 유리 통에 넣으면 돼!', TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (9, 'GLASS_CONTAINER', '유리 용기', '/assets/recycling/items/glass-container.png', 'GLASS',
     '기억해 두자! 유리 용기는 유리 통에 넣으면 돼!', TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6));
