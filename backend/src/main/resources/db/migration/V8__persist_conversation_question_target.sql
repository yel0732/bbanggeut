ALTER TABLE conversation_turn
    ADD COLUMN question_target VARCHAR(30) NULL
        AFTER question_text;
